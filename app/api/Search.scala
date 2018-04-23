package api

import services.{RdfSPARQLService, DatasetService, FileService, CollectionService, PreviewService, MultimediaQueryService, ElasticsearchPlugin}
import play.Logger
import scala.collection.mutable.{ListBuffer, HashMap}
import scala.collection.JavaConversions.mapAsScalaMap
import edu.illinois.ncsa.isda.lsva.ImageMeasures
import edu.illinois.ncsa.isda.lsva.ImageDescriptors.FeatureType
import util.{SearchUtils, SearchResult}
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

  /** Search using a simple text string */
  def search(query: String) = PermissionAction(Permission.ViewDataset) { implicit request =>
    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {
        var filesFound = ListBuffer.empty[String]
        var datasetsFound = ListBuffer.empty[String]
        var collectionsFound = ListBuffer.empty[String]

        val response = plugin.search(query.replaceAll("([:/\\\\])", "\\\\$1"))

        for (resource <- response) {
          resource.resourceType match {
            case ResourceRef.file => filesFound += resource.id.stringify
            case ResourceRef.dataset => datasetsFound += resource.id.stringify
            case ResourceRef.collection => collectionsFound += resource.id.stringify
          }
        }

        Ok(toJson( Map[String,JsValue](
          "files" -> toJson(filesFound),
          "datasets" -> toJson(datasetsFound),
          "collections" -> toJson(collectionsFound)
        )))
      }
     case None => {
       Logger.debug("Search plugin not enabled")
          Ok(views.html.pluginNotEnabled("Text search"))
       }
    }
  }

  /** Search using string-encoded Json object (e.g. built by Advanced Search form) */
  def searchJson(query: String, grouping: String, from: Option[Int], size: Option[Int]) = PermissionAction(Permission.ViewDataset) {
    implicit request =>
      implicit val user = request.user

      current.plugin[ElasticsearchPlugin] match {
        case Some(plugin) => {
          val queryList = Json.parse(query).as[List[JsValue]]
          val results = plugin.search(queryList, grouping, from, size)

          val collectionsResults = results.flatMap { c =>
            if (c.resourceType == ResourceRef.collection) collections.get(c.id) else None
          }
          val datasetsResults = results.flatMap { d =>
            if (d.resourceType == ResourceRef.dataset) datasets.get(d.id) else None
          }
          val filesResults = results.flatMap { f =>
            if (f.resourceType == ResourceRef.file) files.get(f.id) else None
          }

          // Use "distinct" to remove duplicate results.
          Ok(JsObject(Seq(
            "datasets" -> toJson(datasetsResults.distinct),
            "files" -> toJson(filesResults.distinct),
            "collections" -> toJson(collectionsResults.distinct),
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
