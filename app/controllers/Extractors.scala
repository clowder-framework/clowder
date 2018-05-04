package controllers

import models.{Folder, ResourceRef, UUID}
import play.api.mvc.Controller
import api.Permission
import javax.inject.{Inject, Singleton}

import services._

import scala.collection.mutable.ListBuffer

/**
 * Information about extractors.
 */
@Singleton
class Extractors  @Inject() (extractions: ExtractionService,
                             extractorService: ExtractorService,
                             fileService: FileService,
                              datasetService: DatasetService,
                             folders: FolderService,
                             spaces: SpaceService,
                             datasets: DatasetService ) extends Controller with SecuredController {

  def listAllExtractions = ServerAdminAction { implicit request =>
    implicit val user = request.user
    val allExtractions = extractions.findAll()
    Ok(views.html.listAllExtractions(allExtractions))
  }

  def submitFileExtraction(file_id: UUID) = PermissionAction(Permission.EditFile, Some(ResourceRef(ResourceRef.file, file_id))) { implicit request =>
    implicit val user = request.user
    val all_extractors = extractorService.listExtractorsInfo()
    val extractors = all_extractors.filter(!_.process.file.isEmpty)
    fileService.get(file_id) match {

      case Some(file) => {
        val foldersContainingFile = folders.findByFileId(file.id).sortBy(_.name)
        var folderHierarchy = new ListBuffer[Folder]()
        if(foldersContainingFile.length > 0) {
          folderHierarchy = folderHierarchy ++ foldersContainingFile
          var f1: Folder = folderHierarchy.head
          while (f1.parentType == "folder") {
            folders.get(f1.parentId) match {
              case Some(fparent) => {
                folderHierarchy += fparent
                f1 = fparent
              }
              case None =>
            }
          }
        }
        val datasetsContainingFile = datasets.findByFileId(file.id).sortBy(_.name)
        val allDatasets =  (folders.findByFileId(file.id).map(folder => datasets.get(folder.parentDatasetId)).flatten ++ datasetsContainingFile)
        val allDecodedDatasets = ListBuffer.empty[models.Dataset]
        val decodedSpacesContaining= ListBuffer.empty[models.ProjectSpace]
        for (aDataset <- allDatasets) {
          val dDataset = Utils.decodeDatasetElements(aDataset)
          allDecodedDatasets += dDataset
          aDataset.spaces.map{ sp =>
            spaces.get(sp) match {
              case Some(s) => {
                decodedSpacesContaining += Utils.decodeSpaceElements(s)
              }
              case None =>
            }
          }
        }
        Ok(views.html.extractions.submitFileExtraction(extractors, file, folderHierarchy.reverse.toList, decodedSpacesContaining.toList, allDecodedDatasets.toList))
      }
      case None => InternalServerError("File not found")
    }
  }

  def submitDatasetExtraction(ds_id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, ds_id))) { implicit request =>
    implicit val user = request.user
    val all_extractors = extractorService.listExtractorsInfo()
    val extractors = all_extractors.filter(!_.process.dataset.isEmpty)
    datasetService.get(ds_id) match {
      case Some(ds) => Ok(views.html.extractions.submitDatasetExtraction(extractors, ds))
      case None => InternalServerError("Dataset not found")
    }
  }
}