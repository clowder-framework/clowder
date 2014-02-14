/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.Play.current
import services.MongoSalatPlugin
import play.api.Logger
import play.api.libs.json.Json.toJson
import models.AppConfiguration
import services._

/**
 * Admin endpoints for JSON API.
 *
 *
 * @author Luigi Marini
 *
 */
object Admin extends Controller with ApiController {

  /**
   * DANGER
   */
  def deleteAllData = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.Admin)) { request =>
    current.plugin[MongoSalatPlugin].map { mongo =>
      mongo.sources.values.map { source =>
        Logger.debug("**DANGER** Deleting data collections **DANGER**")
        source.collection("collections").drop()
        source.collection("datasets").drop()
        source.collection("previews.chunks").drop()
        source.collection("previews.files").drop()
        source.collection("sections").drop()
        source.collection("uploads.chunks").drop()
        source.collection("uploads.files").drop()
        source.collection("uploadquery").drop()
        source.collection("extractions").drop()
        source.collection("streams").drop()
        Logger.debug("**DANGER** Data deleted **DANGER**")
      }
    }
    Ok(toJson("done"))
  }
  
  
  def removeAdmin = SecuredAction(authorization=WithPermission(Permission.Admin)) { request =>    
      Logger.debug("Removing admin")
      
      (request.body \ "email").asOpt[String].map { email =>        
      	    AppConfiguration.adminExists(email) match {
      	      case true => {
      	        Logger.debug("Removing admin with email " + email)     	        
      	        AppConfiguration.removeAdmin(email)	
      	        
      	        Ok(toJson(Map("status" -> "success")))
      	      }
      	      case false => {
      	    	  Logger.info("Identified admin does not exist.")
      	    	  Ok(toJson(Map("status" -> "notmodified")))
      	      }
      	    }      	          
      }.getOrElse {
        BadRequest(toJson("Missing parameter [email]"))
      }      
  }

}

