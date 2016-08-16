package controllers

import models.{ResourceRef, UUID}
import play.api.mvc.Controller
import api.Permission
import javax.inject.{Inject, Singleton}

import services.{ExtractionService, ExtractorService, FileService, DatasetService}

/**
 * Information about extractors.
 */
@Singleton
class Extractors  @Inject() (extractions: ExtractionService,
  extractorService: ExtractorService, fileService: FileService, datasetService: DatasetService) extends Controller with SecuredController {

  def listAllExtractions = ServerAdminAction { implicit request =>
    implicit val user = request.user
    val allExtractions = extractions.findAll()
    Ok(views.html.listAllExtractions(allExtractions))
  }

  def submitFileExtraction(file_id: UUID) = PermissionAction(Permission.EditFile, Some(ResourceRef(ResourceRef.file, file_id))) { implicit request =>
    val extractors = extractorService.listExtractorsInfo()
    fileService.get(file_id) match {
      case Some(file) => Ok(views.html.extractions.submitFileExtraction(extractors, file))
      case None => InternalServerError("File not found")
    }
  }

  def submitDatasetExtraction(ds_id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, ds_id))) { implicit request =>
    val extractors = extractorService.listExtractorsInfo()
    datasetService.get(ds_id) match {
      case Some(ds) => Ok(views.html.extractions.submitDatasetExtraction(extractors, ds))
      case None => InternalServerError("Dataset not found")
    }
  }
}