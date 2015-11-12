package api

import javax.inject.Inject

import controllers.Utils
import models.{Feature, MultimediaFeatures, UUID}
import play.api.Play.current
import play.api.libs.json.JsObject
import play.api.libs.json.Json._
import play.api.mvc.Controller
import services.{ExtractorMessage, _}

/**
 * Index data.
 */
@Inject
class Indexes @Inject() (multimediaSearch: MultimediaQueryService, previews: PreviewService) extends Controller with ApiController {

  /**
   * Submit section, preview, file for indexing.
   */
  def index() = PermissionAction(Permission.MultimediaIndexDocument)(parse.json) { implicit request =>
      (request.body \ "section_id").asOpt[String].map { section_id =>
      	  (request.body \ "preview_id").asOpt[String].map { preview_id =>
            previews.get(UUID(preview_id)) match {
      	      case Some(p) =>
	      	    // TODO RK need to replace unknown with the server name
	            val key = "unknown." + "index."+ p.contentType.replace(".", "_").replace("/", ".")
	            val host = Utils.baseUrl(request)
	            val id = p.id
	            current.plugin[RabbitmqPlugin].foreach{
                // TODO replace null with None
	              _.extract(ExtractorMessage(id, id, host, key, Map("section_id"->section_id), p.length.toString, null, ""))}
	            val fileType = p.contentType
	            current.plugin[VersusPlugin].foreach{ _.indexPreview(id,fileType) }
	            Ok(toJson("success"))
      	      case None => BadRequest(toJson("Missing parameter [preview_id]"))
            }
      	   
      	  }.getOrElse {
      		BadRequest(toJson("Missing parameter [preview_id]"))
      	  }
      }.getOrElse {
        BadRequest(toJson("Missing parameter [section_id]"))
      }
  }
  
  /**
   * Add feature to index.
   */
  def features() = PermissionAction(Permission.MultimediaIndexDocument)(parse.json) { implicit request =>
      (request.body \ "section_id").asOpt[String].map { section_id =>
        val sectionUUID = UUID(section_id)
        multimediaSearch.findFeatureBySection(sectionUUID) match {
          case Some(multimediaFeature) => {
            val features = (request.body \ "features").as[List[JsObject]]
            multimediaSearch.updateFeatures(multimediaFeature, sectionUUID, features)
            // TODO add method to pre-compute with existing feature vectors
            Ok(toJson(Map("id"->multimediaFeature.id.toString)))
          }
          case None => {
            val jsFeatures = (request.body \ "features").as[List[JsObject]]
            val features = jsFeatures.map {f =>
            	Feature((f \ "representation").as[String], (f \ "descriptor").as[List[Double]])
            }
            val doc = MultimediaFeatures(section_id = Some(sectionUUID), features = features)
            multimediaSearch.insert(doc)
            // precompute distances
            multimediaSearch.computeDistances(doc)
            Ok(toJson(Map("id"->doc.id.toString)))
          }
        }
      }.getOrElse {
        BadRequest(toJson("Missing parameter [section_id]"))
      }
  }
}
