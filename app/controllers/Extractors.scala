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
import javax.inject.{Inject, Singleton}
import services.{ExtractionService, FileService}

/**
 * Information about extractors.
 * 
 * @author Luigi Marini
 *
 */
@Singleton
class Extractors  @Inject() (extractions: ExtractionService) extends Controller with SecuredController {

  def listAllExtractions = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    val allExtractions = extractions.findAll()
    Ok(views.html.extractions(allExtractions))
  }
  
}