package api

import services.{RdfSPARQLService, DatasetService, FileService, CollectionService, PreviewService, MultimediaQueryService, ElasticsearchPlugin}
import play.Logger
import scala.collection.mutable.{ListBuffer, HashMap}
import util.SearchResult
import play.api.libs.json.{JsObject, Json, JsValue}
import play.api.libs.json.Json.toJson
import javax.inject.{Inject, Singleton}
import play.api.Play.current
import play.api.Play.configuration
import models._

@Singleton
class Search @Inject() (
   files: FileService,
   datasets: DatasetService,
   collections: CollectionService,
   previews: PreviewService,
   queries: MultimediaQueryService,
   sparql: RdfSPARQLService)  extends ApiController {

  /** Search using a simple text string with filters */
  def search(query: String, resource_type: Option[String],
             datasetid: Option[String], collectionid: Option[String], spaceid: Option[String], folderid: Option[String],
             field: Option[String], tag: Option[String]) = PermissionAction(Permission.ViewDataset) { implicit request =>
    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {
        val response = plugin.searchWithParameters(query, resource_type, datasetid, collectionid, spaceid, folderid, field, tag)

        var filesFound = ListBuffer.empty[UUID]
        var datasetsFound = ListBuffer.empty[UUID]
        var collectionsFound = ListBuffer.empty[UUID]

        for (resource <- response) {
          resource.resourceType match {
            case ResourceRef.file => filesFound += resource.id
            case ResourceRef.dataset => datasetsFound += resource.id
            case ResourceRef.collection => collectionsFound += resource.id
            case other => Logger.debug(s"search result resource type not supported: ${other.toString}")
          }
        }

        val filesList = files.get(filesFound.toList).map(f => toJson(f))
        val datasetsList = datasets.get(datasetsFound.toList).map(ds => toJson(ds))
        val collectionsList = collections.get(collectionsFound.toList).map(c => toJson(c))

        resource_type match {
          case Some("file") => Ok(toJson(Map[String, JsValue]("files" -> toJson(filesList))))
          case Some("dataset") => Ok(toJson(Map[String, JsValue]("datasets" -> toJson(datasetsList))))
          case Some("collection") => Ok(toJson(Map[String, JsValue]("collections" -> toJson(collectionsList))))

          case _ => {
            // If datasetid is provided, only files are returned
            datasetid match {
              case Some(dsid) => Ok(toJson(Map[String, JsValue](
                "files" -> toJson(filesList)
              )))
              case None => {
                // collection and space IDs do not restrict resource type
                Ok(toJson(Map[String, JsValue](
                  "files" -> toJson(filesList),
                  "datasets" -> toJson(datasetsList),
                  "collections" -> toJson(collectionsList)
                )))
              }
            }
          }
        }
      }
      case None => {
        Logger.debug("Search plugin not enabled")
        Ok(views.html.pluginNotEnabled("Text search"))
      }
    }
  }

  /** Search using string-encoded Json object (e.g. built by Metadata Search form) */
  def searchJson(query: String, grouping: String, from: Option[Int], size: Option[Int]) = PermissionAction(Permission.ViewDataset) {
    implicit request =>
      implicit val user = request.user

      current.plugin[ElasticsearchPlugin] match {
        case Some(plugin) => {
          val queryList = Json.parse(query).as[List[JsValue]]
          val results = plugin.search(queryList, grouping, from, size)

          var filesFound = ListBuffer.empty[UUID]
          var datasetsFound = ListBuffer.empty[UUID]
          var collectionsFound = ListBuffer.empty[UUID]

          for (resource <- results) {
            resource.resourceType match {
              case ResourceRef.file => filesFound += resource.id
              case ResourceRef.dataset => datasetsFound += resource.id
              case ResourceRef.collection => collectionsFound += resource.id
              case _ => {}
            }
          }

          val filesList = files.get(filesFound.toList).map(f => toJson(f))
          val datasetsList = datasets.get(datasetsFound.toList).map(ds => toJson(ds))
          val collectionsList = collections.get(collectionsFound.toList).map(c => toJson(c))

          // Use "distinct" to remove duplicate results.
          Ok(JsObject(Seq(
            "datasets" -> toJson(datasetsList.distinct),
            "files" -> toJson(filesList.distinct),
            "collections" -> toJson(collectionsList.distinct),
            "count" -> toJson(results.length)
          )))
        }
        case None => {
          BadRequest("Elasticsearch plugin could not be reached")
        }
      }
  }

  def querySPARQL() = PermissionAction(Permission.ViewMetadata) { implicit request =>
      configuration.getString("userdfSPARQLStore").getOrElse("no") match {
        case "yes" => {
          val queryText = request.body.asFormUrlEncoded.get("query").apply(0)
          Logger.debug("whole msg: " + request.toString)
          val resultsString = sparql.sparqlQuery(queryText)
          Logger.debug("SPARQL query results: " + resultsString)
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
  def searchMultimediaIndex(section_id: UUID) = PermissionAction(Permission.ViewSection,
    Some(ResourceRef(ResourceRef.section, section_id))) {
    implicit request =>

    // Finding IDs of spaces that the user has access to
    val user = request.user
    val spaceIDsList = user.get.spaceandrole.map(_.spaceId)
    Logger.debug("Searching multimedia index " + section_id.stringify)

    // TODO handle multiple previews found
    val preview = previews.findBySectionId(section_id)(0)
    queries.findFeatureBySection(section_id) match {

      case Some(feature) => {
        // setup priority queues
        val queues = new HashMap[String, List[MultimediaDistance]]
        val representations = feature.features.map { f =>
          queues(f.representation) = queries.searchMultimediaDistances(section_id.toString,f.representation,20, spaceIDsList)
        }

        val items = new HashMap[String, ListBuffer[SearchResult]]
        queues map {
          case (key, queue) =>
            val list = new ListBuffer[SearchResult]
            for (element <- queue) {              
              val previewsBySection = previews.findBySectionId(element.target_section)
              if (previewsBySection.size == 1) {
                Logger.trace("Appended search result " + key + " " + element.target_section + " " + element.distance + " " + previewsBySection(0).id.toString)
                list.append(SearchResult(element.target_section.toString, element.distance, Some(previewsBySection(0).id.toString)))
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
