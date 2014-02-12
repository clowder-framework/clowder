/**
 *
 */
package controllers

import play.api.mvc.Controller
import models.Extraction
import play.api.mvc.WebSocket
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Enumerator
import play.api.mvc.Action
import api.WithPermission
import api.Permission

/**
 * Information about extractors.
 * 
 * @author Luigi Marini
 *
 */
object Extractors extends Controller with SecuredController {

  def extractions = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
    Ok(views.html.extractions(Extraction.findAll.toList))
  }
  
}