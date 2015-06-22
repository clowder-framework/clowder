/**
 *
 */
package controllers

import api.Permission
import play.api.mvc.Controller

/**
 * Geostreaming playground.
 */
object Geostreams extends Controller with SecuredController {
  
  def browse() = PermissionAction(Permission.ViewGeoStream) { implicit request =>
    implicit val user = request.user
    Ok(views.html.geostreams())
  }
  
}