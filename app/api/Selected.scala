package api

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.json.Json._
import services.mongodb.SelectedDAO
import play.api.Logger
import javax.inject.Inject
import services.SelectionService
import models.UUID

/**
 * Selected items.
 * 
 * @author Luigi Marini
 *
 */
class Selected @Inject()(selections: SelectionService) extends Controller with ApiController {

  def add() = SecuredAction(authorization = WithPermission(Permission.AddSelection))  { implicit request =>
    Logger.debug("Requesting Selected.add" + request.body)
    request.body.\("dataset").asOpt[String] match {
	    case Some(dataset) => {
        request.user match {
          case Some(user) => {
            selections.add(UUID(dataset), user.email.get)
            Ok(toJson(Map("success"->"true")))
          }
          case None => Ok(toJson(Map("success"->"false", "msg"->"User not logged in")))
        }
	    }
	    case None => {
	    	Logger.error("no dataset specified")
	    	BadRequest
	    }
    }
  }
  
  def remove() = SecuredAction(authorization = WithPermission(Permission.RemoveSelection))  { implicit request =>
    Logger.debug("Requesting Selected.remove" + request.body)
    request.body.\("dataset").asOpt[String] match {
	    case Some(dataset) => {
        request.user match {
          case Some(user) => {
            selections.remove(UUID(dataset), user.email.get)
            Ok(toJson(Map("success"->"true")))
          }
          case None => Ok(toJson(Map("success"->"false", "msg"->"User not logged in")))
        }
	    }
	    case None => {
	    	Logger.error("no dataset specified")
	    	BadRequest
	    }
    }
  }
}