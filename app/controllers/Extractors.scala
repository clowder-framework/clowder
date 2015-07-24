package controllers

import models.UUID
import play.api.mvc.Controller
import api.WithPermission
import api.Permission
import javax.inject.{Inject, Singleton}
import services.{ExtractorService, ExtractionService}

/**
 * Information about extractors.
 */
@Singleton
class Extractors  @Inject() (extractions: ExtractionService, extractorService: ExtractorService) extends Controller with SecuredController {

  def listAllExtractions = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
    val allExtractions = extractions.findAll()
    Ok(views.html.listAllExtractions(allExtractions))
  }

  def submitExtraction(file_id: UUID) = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    val extractors = extractorService.listExtractorsInfo()
    Ok(views.html.extractions.submitExtraction(extractors, file_id))
  }
}