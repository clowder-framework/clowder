/**
 *
 */
package controllers

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.Routes

/**
 * Administration pages.
 * 
 * @author Luigi Marini
 *
 */
object Admin extends Controller with securesocial.core.SecureSocial {
  
  def main = Action { implicit request =>
    Ok(views.html.admin())
  }
  
  def reindexFiles = SecuredAction() { implicit request =>
    Ok("")
  }
  
  def test() = Action {
    Ok("""{"message":"test"}""").as(JSON)
  }
  
  def secureTest() = SecuredAction() { implicit request =>
    Ok("""{"message":"secure test"}""").as(JSON)
  }
}