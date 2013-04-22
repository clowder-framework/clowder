/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.Json._
import models.Dataset
import play.Logger

/**
 * Dataset API.
 * 
 * @author Luigi Marini
 *
 */
object Datasets extends Controller {

	def addMetadata(id: String) = Authenticated {
	  Logger.debug("Adding metadata to dataset " + id)
	    Action(parse.json) { request =>
	          Dataset.addMetadata(id, Json.stringify(request.body))
	          Ok(toJson(Map("status"->"success")))
	    }
	}
}