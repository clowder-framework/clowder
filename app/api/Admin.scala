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
  def deleteAllData = Authenticated {
    Action {
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
  }
}