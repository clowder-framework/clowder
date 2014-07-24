/**
 *
 */
package controllers

import play.api.mvc.Controller
import api.WithPermission
import api.Permission

/**
 * Geostreaming playground.
 * 
 * @author Luigi Marini
 *
 */
object Geostreams extends Controller with SecuredController {
  
  def browse() = SecuredAction(authorization=WithPermission(Permission.SearchSensors)) { implicit request =>
    implicit val user = request.user
    Ok(views.html.geostreams())
  }
  
}