/**
 *
 */
package controllers

import play.api.mvc.Controller

/**
 * Geostreaming playground.
 * 
 * @author Luigi Marini
 *
 */
object Geostreams extends Controller with SecuredController {
  
  def browse() = SecuredAction(parse.anyContent, allowKey=false) { implicit request =>
    implicit val user = request.user
    Ok(views.html.geostreams())
  }
  
}