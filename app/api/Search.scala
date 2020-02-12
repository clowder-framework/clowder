package api

import api.Permission._
import services.{DatasetService, FileService, CollectionService, PreviewService, SpaceService,
  MultimediaQueryService, SearchService}
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
   spaces: SpaceService,
   searches: SearchService)  extends ApiController {

  /** Search using a simple text string with filters */
  def search(query: String, resource_type: Option[String], datasetid: Option[String], collectionid: Option[String],
             spaceid: Option[String], folderid: Option[String], field: Option[String], tag: Option[String],
             from: Option[Int], size: Option[Int], page: Option[Int]) = PermissionAction(Permission.ViewDataset) { implicit request =>
    if (searches.isEnabled) {
      // If from is specified, use it. Otherwise use page * size of page if possible, otherwise use 0.
      val from_index = from match {
        case Some(f) => from
        case None => page match {
          case Some(p) => Some(size.getOrElse(0) * p)
          case None => None
        }
      }

      // Add space filter to search here as a simple permissions check
      val permitted = spaces.listAccess(0, Set[Permission](Permission.ViewSpace), request.user, true, true, false, false).map(sp => sp.id)

      val result = searches.search(query, resource_type, datasetid, collectionid, spaceid, folderid, field, tag, from_index, size, permitted, request.user)

      Ok(toJson(result))
    } else {
      Logger.debug("Search plugin not enabled")
      Ok(views.html.pluginNotEnabled("Text search"))
    }
  }

  /** Search using string-encoded Json object (e.g. built by Metadata Search form) */
  def searchJson(query: String, grouping: String, from: Option[Int], size: Option[Int]) = PermissionAction(Permission.ViewDataset) {
    implicit request => {
      implicit val user = request.user

      if (searches.isEnabled) {
        val queryList = Json.parse(query).as[List[JsValue]]
        val result = searches.search(queryList, grouping, from, size, user)
        Ok(toJson(result))
      } else {
        BadRequest("Elasticsearch plugin could not be reached")
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
