/**
 *
 */
package controllers

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.Routes
import securesocial.core.SecureSocial._
import securesocial.core.SecureSocial
import api.ApiController

/**
 * Administration pages.
 * 
 * @author Luigi Marini
 *
 */
object Admin extends Controller with SecuredController {
  
  def main = Action { implicit request =>
    Ok(views.html.admin())
  }
  
  def reindexFiles = SecuredAction(parse.json, allowKey=false) { implicit request =>
    Ok("Reindexing")
  }
  
  def test = Action {
    Ok("""{"message":"test"}""").as(JSON)
  }
  
  def secureTest = SecuredAction(parse.json) { implicit request =>
    Ok("""{"message":"secure test"}""").as(JSON)
  }
}