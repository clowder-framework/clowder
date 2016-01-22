package controllers

import models._
import api.Permission
import services._
import javax.inject.Inject
import play.api.Logger

/**
 *
 */
class Folders @Inject() (
  datasets: DatasetService,
  folderService: FolderService
  )extends SecuredController{

  def newFolder(parentType: String, parentId: Option[String], parentDatasetId:UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, parentDatasetId))) { implicit request =>
    implicit val user = request.user
    datasets.get(parentDatasetId) match {
      case Some(parentDataset) => {
        if(parentType == "dataset") {
          Ok(views.html.datasets.createFolder(parentType, UUID(parentId.getOrElse(parentDatasetId.stringify)), parentDataset.name, parentDatasetId, parentDataset.name))
        } else if(parentType == "folder") {
          parentId match {
            case Some(folderId) => {
              folderService.get(UUID(folderId)) match {
                case Some(folder) => {
                  Ok(views.html.datasets.createFolder(parentType, UUID(parentId.getOrElse(parentDatasetId.stringify)), folder.name, parentDatasetId, parentDataset.name))
                }
                case None => InternalServerError(s"No folder with id $folderId found")
              }
            }
            case None => InternalServerError(s"No Parent Id provided for type $parentType")

          }
        } else {
          InternalServerError(s"No parent type $parentType found")
        }
      }
      case None =>   InternalServerError(s"Parent Dataset $parentDatasetId not found")
    }

  }

  def submit(parentDatasetId: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, parentDatasetId)))(parse.multipartFormData) { implicit request =>
    implicit val user = request.user
    Logger.debug("--- Creating Folder -----")
    val folderName = request.body.asFormUrlEncoded.getOrElse("name" , null)
    val folderParentId = request.body.asFormUrlEncoded.getOrElse("parentId", null)
    val folderParentType = request.body.asFormUrlEncoded.getOrElse("parentType", null)

    user match {
      case Some(identity) => {
        datasets.get(parentDatasetId) match {
          case Some(parentDataset) => {
            var folder: Folder = null
            if(UUID(folderParentId(0)) == parentDatasetId) {
              folder = Folder(name= folderName(0), files = List.empty, folders = List.empty, parentId = parentDatasetId, parentType = folderParentType(0)  )
            } else if(folderParentType.toString() == "folder") {
              folderService.get(UUID(folderParentId.toString())) match {
                case Some(pfolder) => {
                  folder = Folder(name= folderName(0), files = List.empty, folders = List.empty, parentId = UUID(folderParentId.toString()), parentType = folderParentType(0)  )
                }
                case None => InternalServerError(s"Parent Folder $folderParentId not found")
              }
            } else {
              InternalServerError(s"Parent type $folderParentType not acceptable")
            }
            Logger.debug(s"Saving folder $folderName")
            if(folder != null) {
              folderService.insert(folder)
              if(folderParentType(0) == "dataset" && UUID(folderParentId(0)) == parentDatasetId) {
                datasets.addFolder(parentDatasetId, folder.id)
              } else if(folderParentType == "folder") {
                folderService.get(UUID(folderParentId.toString())) match {
                  case Some(pfolder) => {
                    folderService.addSubFolder(pfolder.id, folder.id)
                  }
                  case None => InternalServerError(s"Parent Folder $folderParentId not found")
                }
              }

              //TODO: Add Created Folder  event
              Redirect(routes.Datasets.dataset(parentDataset.id))
            } else {
              InternalServerError("Unable to create the folder")
            }


          }
          case None => InternalServerError(s"Parent Dataset $parentDatasetId not found")
        }

      }
    }
  }

}
