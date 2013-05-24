package controllers

import play.api.mvc.Controller
import securesocial.core.SecureSocial
import play.api.Logger
import play.api.libs.json.Json._
import models.Dataset
import play.api.mvc.Action
import models.File
import models.FileDAO

/**
 * Tagging.
 * 
 * @author Luigi Marini
 */
object Tags extends Controller with SecureSocial {
  
  def tag() = SecuredAction(ajaxCall = true) { implicit request =>
    Logger.debug("Tagging " + request.body)
    
    request.body.asJson.map {json =>
      (json \ "id").asOpt[String].map { id =>
        (json \ "tag").asOpt[String].map { tag =>
          Logger.debug("Tagging " + id + " with " + tag)
          Dataset.tag(id, tag)
        }
      }
      Ok(toJson(""))
    }.getOrElse {
      BadRequest(toJson("error"))
    }
  }
  
  def search(tag: String) = Action {
    val datasets = Dataset.findByTag(tag)
    val files = FileDAO.findByTag(tag)
//    Logger.debug("Search by tag " + tag + " returned " + datasets.length)
    Ok(views.html.searchByTag(tag, datasets, files))
  }
}