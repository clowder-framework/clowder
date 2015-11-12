package api

import services.{RdfSPARQLService, DatasetService, FileService, CollectionService, PreviewService, MultimediaQueryService, ElasticsearchPlugin}
import play.Logger
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConversions.mapAsScalaMap
import edu.illinois.ncsa.isda.lsva.ImageMeasures
import edu.illinois.ncsa.isda.lsva.ImageDescriptors.FeatureType
import scala.collection.mutable.{ HashMap, ListBuffer }
import util.DistancePriorityQueue
import util.SearchResult
import util.MultimediaIndex
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import javax.inject.{Inject, Singleton}
import play.api.Play.current
import play.api.Play.configuration
import models.UUID
import models.MultimediaDistance
import com.wordnik.swagger.annotations.{ApiOperation, Api}

@Singleton
class Search @Inject() (files: FileService, datasets: DatasetService, collections: CollectionService, previews: PreviewService, queries: MultimediaQueryService, sparql: RdfSPARQLService)  extends ApiController {
  /**
   * Search results.
   */
  def search(query: String) = PermissionAction(Permission.ViewDataset) { implicit request =>
    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {
        Logger.debug("Searching for: " + query)

        var filesFound = ListBuffer.empty[models.File]
        var datasetsFound = ListBuffer.empty[models.Dataset]
        var collectionsFound = ListBuffer.empty[models.Collection]

        if (query != "") {
          
          val result = current.plugin[ElasticsearchPlugin].map { _.search("data", query.replaceAll("([:/\\\\])", "\\\\$1"))}
          
          result match {
            case Some(searchResponse) => {
              for (hit <- searchResponse.getHits().getHits()) {
                Logger.debug("Computing search result " + hit.getId())
                Logger.info("Fields: ")
                for ((key, value) <- mapAsScalaMap(hit.getFields())) {
                  Logger.info(value.getName + " = " + value.getValue())
                }
                if (hit.getType() == "file") {
                  files.get(UUID(hit.getId())) match {
                    case Some(file) => {
                      Logger.debug("FILES:hits.hits._id: Search result found file " + hit.getId());
                      Logger.debug("FILES:hits.hits._source: Search result found dataset " + hit.getSource().get("datasetId"))
                      //Logger.debug("Search result found file " + hit.getId()); files += file

                      filesFound += file

                    }
                    case None => Logger.debug("File not found " + hit.getId())
                   }
                  } else if (hit.getType() == "dataset") {
                    Logger.debug("DATASETS:hits.hits._source: Search result found dataset " + hit.getSource().get("name"))
                    Logger.debug("DATASETS:Dataset.id=" + hit.getId());
                    datasets.get(UUID(hit.getId())) match {
                      case Some(dataset) =>
                        Logger.debug("Search result found dataset" + hit.getId()); datasetsFound += dataset
                      case None => {
                        Logger.debug("Dataset not found " + hit.getId())
                        //Redirect(routes.Datasets.dataset(hit.getId))
                      }
                    }
                  }
                else if (hit.getType() == "collection") {
                  Logger.debug("COLLECTIONS:hits.hits._source: Search result found collection " + hit.getSource().get("name"))
                  Logger.debug("COLLECTIONS:Collection.id=" + hit.getId())
                  
                  collections.get(UUID(hit.getId())) match {
                    case Some(collection) =>
                      Logger.debug("Search result found collection" + hit.getId()); collectionsFound += collection
                    case None => {
                      Logger.debug("Collection not found " + hit.getId())
                    }
                  }
                  
                }
              }
             }
              case None => Logger.debug("Search returned no results")
              
            }
        }
        
        val filesJson = toJson(for(currFile <- filesFound.toList) yield {
          currFile.id.stringify
        } )
        val datasetsJson = toJson(for(currDataset <- datasetsFound.toList) yield {
          currDataset.id.stringify
        } )
        val collectionsJson = toJson(for(currCollection <- collectionsFound.toList) yield {
          currCollection.id.stringify
        } )
        
        val fullJSON = toJson(Map[String,JsValue]("files" -> filesJson, "datasets" -> datasetsJson, "collections" -> collectionsJson))
        
        Ok(fullJSON)
      }
     case None => {
       Logger.debug("Search plugin not enabled")
          Ok(views.html.pluginNotEnabled("Text search"))
       }
    }
  }
  
  def querySPARQL() = PermissionAction(Permission.ViewMetadata) { implicit request =>
      configuration.getString("userdfSPARQLStore").getOrElse("no") match {
        case "yes" => {
          val queryText = request.body.asFormUrlEncoded.get("query").apply(0)
          Logger.info("whole msg: " + request.toString)
          val resultsString = sparql.sparqlQuery(queryText)
          Logger.info("SPARQL query results: " + resultsString)
          Ok(resultsString)
        }
        case _ => {
          Logger.error("RDF SPARQL store not used.")
          InternalServerError("Error searching RDF store. RDF SPARQL store not used.")
        }
      }
  }

  /**
   * Search MultimediaFeatures.
   */
   @ApiOperation(value = "Search multimedia index for similar sections",
       notes = "",
       responseClass = "None", httpMethod = "GET")
  def searchMultimediaIndex(section_id: UUID) = PermissionAction(Permission.ViewDataset) {
    implicit request =>
    Logger.debug("Searching multimedia index " + section_id.stringify)
    // TODO handle multiple previews found
    val preview = previews.findBySectionId(section_id)(0)
    queries.findFeatureBySection(section_id) match {
      case Some(feature) => {
        // setup priority queues
        val queues = new HashMap[String, DistancePriorityQueue]
        val representations = feature.features.map { f =>
          queues(f.representation) = new DistancePriorityQueue(20)
        }
        // push into priority queues
        feature.features.map { f =>
          Logger.debug("Computing " + f.representation)
          val cursor = queries.listAll()
          while (cursor.hasNext) {
            val mf = cursor.next          
            Logger.debug("Found multimedia features " + mf.id + " for section " + section_id)
            mf.features.find(_.representation == f.representation) match {
              case Some(fd) => {
                val distance = ImageMeasures.getDistance(FeatureType.valueOf(fd.representation), f.descriptor.toArray, fd.descriptor.toArray)
                if (!distance.isNaN()) {
                  Logger.trace(f.representation + "/" + fd.representation + " Distance between " + feature.section_id + " and " + mf.id + " is " + distance)
                  queues.get(f.representation).map { q =>
                    val popped = q.insertWithOverflow(SearchResult(mf.section_id.get.toString, distance, None))
                    if (popped != null) Logger.trace("Popped distance off the queue " + popped)
                  }
                } else {
                  Logger.debug("Distance NaN " + f.descriptor + " and " + fd.descriptor + " is " + distance)
                }
              }
              case None =>
                Logger.error("Matching descriptor not found")
            }
          }
        }

        val items = new HashMap[String, ListBuffer[SearchResult]]
        queues map {
          case (key, queue) =>
            val list = new ListBuffer[SearchResult]
            while (queue.size > 0) {
              val element = queue.pop()
              val previewsBySection = previews.findBySectionId(UUID(element.section_id))
              if (previewsBySection.size == 1) {
                Logger.trace("Appended search result " + key + " " + element.section_id + " " + element.distance + " " + previewsBySection(0).id.toString)
                list.prepend(SearchResult(element.section_id, element.distance, Some(previewsBySection(0).id.toString)))
              } else {
                Logger.error("Found more/less than one preview " + preview)
              }
            }
            items += key -> list
        }

        val jsonResults = toJson(items.toMap)
        Ok(jsonResults)
                
      }
      case None => InternalServerError("feature not found")
    }
  }


  /**
   * Search MultimediaFeatures.
   */
   @ApiOperation(value = "Search multimedia index for similar sections",
       notes = "",
       responseClass = "None", httpMethod = "GET")
  def searchMultimediaIndexNew(section_id: UUID) = PermissionAction(Permission.ViewDataset) {
    implicit request =>
    
    Logger.debug("Searching multimedia index " + section_id.stringify)
    // TODO handle multiple previews found
    val preview = previews.findBySectionId(section_id)(0)
    queries.findFeatureBySection(section_id) match {

      case Some(feature) => {
        // setup priority queues
        val queues = new HashMap[String, List[MultimediaDistance]]
        val representations = feature.features.map { f =>
          queues(f.representation) = queries.searchMultimediaDistances(section_id.toString,f.representation,20)
        }        

        val items = new HashMap[String, ListBuffer[SearchResult]]
        queues map {
          case (key, queue) =>
            val list = new ListBuffer[SearchResult]
            for (element <- queue) {              
              val previewsBySection = previews.findBySectionId(element.target_section)
              if (previewsBySection.size == 1) {
                Logger.trace("Appended search result " + key + " " + element.target_section + " " + element.distance + " " + previewsBySection(0).id.toString)
                list.prepend(SearchResult(element.target_section.toString, element.distance, Some(previewsBySection(0).id.toString)))
              } else {
                Logger.error("Found more/less than one preview " + preview)
              }
            }
            items += key -> list
        }

        val jsonResults = toJson(items.toMap)
        Ok(jsonResults)
                
      }
      case None => InternalServerError("feature not found")
    }
  }
}
