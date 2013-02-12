/**
 *
 */
package controllers

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.Routes
import securesocial.core.SecureSocial._
import securesocial.core.SecureSocial

/**
 * Administration pages.
 * 
 * @author Luigi Marini
 *
 */
object Admin extends Controller with SecureSocial {
  
  def main = Action { implicit request =>
    Ok(views.html.admin())
  }
  
  def reindexFiles = SecuredAction { implicit request =>
    Ok("Reindexing")
  }
  
  def test = Action {
    Ok("""{"message":"test"}""").as(JSON)
  }
  
  def secureTest = SecuredAction { implicit request =>
    Ok("""{"message":"secure test"}""").as(JSON)
  }
}