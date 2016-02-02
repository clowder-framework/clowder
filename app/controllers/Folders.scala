package controllers

import api.Permission
import models.{ResourceRef, UUID}
import services._
import javax.inject.Inject

/**
  */
class Folders @Inject()(datasets: DatasetService, folders: FolderService) extends SecuredController{

  def addFiles(id: UUID, folderId: String) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    datasets.get(id) match {
      case Some(dataset) => {
        folders.get(UUID(folderId)) match {
          case Some(folder) => Ok(views.html.datasets.addFiles(dataset, Some(folder)))
          case None => Ok(views.html.datasets.addFiles(dataset, None))
        }
      }
      case None => {
        InternalServerError(s"Dataset $id not found")
      }
    }
  }

}
