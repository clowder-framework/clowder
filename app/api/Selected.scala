/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.json.Json._
import models.SelectedDAO
import play.api.Logger

/**
 * Selected items.
 * 
 * @author Luigi Marini
 *
 */
object Selected extends Controller with ApiController {
  def add() = SecuredAction(parse.json, allowKey=false)  { implicit request =>
    Logger.debug("Requesting Selected.add" + request.body)
    request.body.\("dataset").asOpt[String] match {
	    case Some(dataset) => {
		    SelectedDAO.add(dataset, request.user.email.get)
		    Ok(toJson(Map("success"->"true")))
	    }
	    case None => {
	    	Logger.error("no dataset specified")
	    	BadRequest
	    }
    }
  }
  
  def remove() = SecuredAction(parse.json, allowKey=false)  { implicit request =>
    Logger.debug("Requesting Selected.remove" + request.body)
    request.body.\("dataset").asOpt[String] match {
	    case Some(dataset) => {
		    SelectedDAO.remove(dataset, request.user.email.get)
		    Ok(toJson(Map("success"->"true")))
	    }
	    case None => {
	    	Logger.error("no dataset specified")
	    	BadRequest
	    }
    }
  }
}