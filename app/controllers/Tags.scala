package controllers

import play.api.mvc.Controller
import securesocial.core.SecureSocial
import play.api.Logger
import play.api.libs.json.Json._
import models.Dataset
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
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
          val result = Dataset.dao.collection.update(
          MongoDBObject("_id" -> new ObjectId(id)), 
          	$addToSet("tags" -> tag), false, false, WriteConcern.Safe)
        }
      }
      Ok(toJson(""))
    }.getOrElse {
      BadRequest(toJson("error"))
    }
  }
}