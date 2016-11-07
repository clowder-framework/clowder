package controllers

import models.{ResourceRef, UUID}
import play.api.mvc.Controller
import api.Permission
import javax.inject.{Inject, Singleton}

import services.{ExtractionService, ExtractorService, FileService}

/**
 * Information about extractors.
 */
@Singleton
class Extractors  @Inject() (extractions: ExtractionService,
  extractorService: ExtractorService, fileService: FileService) extends Controller with SecuredController {

  def listAllExtractions = ServerAdminAction { implicit request =>
    implicit val user = request.user
    val allExtractions = extractions.findAll()
    Ok(views.html.listAllExtractions(allExtractions))
  }

  def submitExtraction(file_id: UUID) = PermissionAction(Permission.EditFile, Some(ResourceRef(ResourceRef.file, file_id))) { implicit request =>
    implicit val user = request.user

    val extractors = extractorService.listExtractorsInfo()
    fileService.get(file_id) match {
      case Some(file) => Ok(views.html.extractions.submitExtraction(extractors, file))
      case None => InternalServerError("File not found")
    }
  }
}