package api

import api.Permission._
import services.{RdfSPARQLService, PreviewService, SpaceService, MultimediaQueryService, ElasticsearchPlugin}
import play.Logger
import scala.collection.mutable.{ListBuffer, HashMap}
import util.{SearchUtils, SearchResult}
import play.api.libs.json.{JsObject, Json, JsValue}
import play.api.libs.json.Json.toJson
import javax.inject.{Inject, Singleton}
import play.api.Play.current
import play.api.Play.configuration
import models._

@Singleton
class Search @Inject() (
   previews: PreviewService,
   queries: MultimediaQueryService,
   spaces: SpaceService,
   sparql: RdfSPARQLService)  extends ApiController {

  /** Search using a simple text string with filters */
  def search(query: String, resource_type: Option[String], datasetid: Option[String], collectionid: Option[String],
             spaceid: Option[String], folderid: Option[String], field: Option[String], tag: Option[String],
             from: Option[Int], size: Option[Int], page: Option[Int], sort: Option[String], order: Option[String]) = PermissionAction(Permission.ViewDataset) { implicit request =>
    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {
        // If from is specified, use it. Otherwise use page * size of page if possible, otherwise use 0.
        val from_index = from match {
          case Some(f) => from
          case None => page match {
            case Some(p) => Some(size.getOrElse(0) * p)
            case None => None
          }
        }

        // TODO: Better way to build a URL?
        val source_url = s"/api/search?query=$query" +
          (resource_type match {case Some(x) => s"&resource_type=$x" case None => ""}) +
          (datasetid match {case Some(x) => s"&datasetid=$x" case None => ""}) +
          (collectionid match {case Some(x) => s"&collectionid=$x" case None => ""}) +
          (spaceid match {case Some(x) => s"&spaceid=$x" case None => ""}) +
          (folderid match {case Some(x) => s"&folderid=$x" case None => ""}) +
          (field match {case Some(x) => s"&field=$x" case None => ""}) +
          (tag match {case Some(x) => s"&tag=$x" case None => ""}) +
          (sort match {case Some(x) => s"&sort=$x" case None => ""}) +
          (order match {case Some(x) => s"&order=$x" case None => ""})

        // Add space filter to search here as a simple permissions check
        val superAdmin = request.user match {
          case Some(u) => u.superAdminMode
          case None => false
        }
        val permitted = if (superAdmin)
          List[UUID]()
        else
          spaces.listAccess(0, Set[Permission](Permission.ViewSpace), request.user, true, true, false, false).map(sp => sp.id)

        val response = plugin.search(query, resource_type, datasetid, collectionid, spaceid, folderid, field, tag, from_index, size, sort, order, permitted, request.user)
        val result = SearchUtils.prepareSearchResponse(response, source_url, request.user)
        Ok(toJson(result))
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
          // Add space filter to search here as a simple permissions check
          val superAdmin = request.user match {
            case Some(u) => u.superAdminMode
            case None => false
          }
          val permitted = if (superAdmin)
            List[UUID]()
          else
            spaces.listAccess(0, Set[Permission](Permission.ViewSpace), request.user, true, true, false, false).map(sp => sp.id)


          val queryList = Json.parse(query).as[List[JsValue]]
          val response = plugin.search(queryList, grouping, from, size, permitted, user)

          // TODO: Better way to build a URL?
          val source_url = s"/api/search?query=$query&grouping=$grouping"

          val result = SearchUtils.prepareSearchResponse(response, source_url, user)
          Ok(toJson(result))
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
  def searchMultimediaIndex(section_id: UUID) = PermissionAction(Permission.ViewSection, Some(ResourceRef(ResourceRef.section, section_id))) {
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
