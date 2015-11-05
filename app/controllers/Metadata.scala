package controllers

import javax.inject.Inject

import api.{WithPermission, Permission}
import models.{Dataset, ResourceRef, UUID}
import services._

/**
 * View JSON-LD metadata for all resources.
 */
class Metadata @Inject() (
  files: FileService,
  datasets: DatasetService,
  metadata: MetadataService, contextLDService: ContextLDService) extends SecuredController {

  def view(id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowFilesMetadata)) { implicit request =>
    metadata.getMetadataById(id) match {
      case Some(m) => Ok(views.html.metadatald.view(List(m)))
      case None => NotFound
    }
  }

  def file(file_id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowFilesMetadata)) { implicit request =>
    implicit val user = request.user
    files.get(file_id) match {
      case Some(file) => {
        val mds = metadata.getMetadataByAttachTo(ResourceRef(ResourceRef.file, file_id))
        // TODO use to provide contextual definitions directly in the GUI
        val contexts = (for (md <- mds;
                             cId <- md.contextId;
                             c <- contextLDService.getContextById(cId))
          yield cId -> c).toMap

        Ok(views.html.metadatald.viewFile(file, mds))
      }
      case None => NotFound
    }
  }

  def dataset(dataset_id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowFilesMetadata)) { implicit request =>
    implicit val user = request.user
    datasets.get(dataset_id) match {
      case Some(dataset) => {
        val m = metadata.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, dataset_id))
        Ok(views.html.metadatald.viewDataset(dataset, m))
      }
      case None => NotFound
    }
  }

  def search() = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) {implicit request =>
    implicit val user = request.user
    Ok(views.html.metadatald.search())
  }
}
