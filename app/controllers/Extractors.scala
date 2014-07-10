package controllers

import play.api.mvc.Controller
import api.WithPermission
import api.Permission
import javax.inject.{Inject, Singleton}
import services.ExtractionService

/**
 * Information about extractors.
 * 
 * @author Luigi Marini
 *
 */
@Singleton
class Extractors  @Inject() (extractions: ExtractionService) extends Controller with SecuredController {

  def listAllExtractions = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
    val allExtractions = extractions.findAll()
    Ok(views.html.extractions(allExtractions))
  }
}
