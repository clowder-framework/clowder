package controllers

import api.Permission
import models.{Folder, ResourceRef, UUID}
import play.api.Logger
import play.api.libs.json.Json._
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

  def createFolder(parentDatasetId: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, parentDatasetId)))(parse.json){ implicit request =>
    Logger.debug("--- API Creating new folder ---- ")
    (request.body \ "name").asOpt[String].map {
      name => {
        (request.body \ "parentId").asOpt[String].map {
          parentId => {
            (request.body \ "parentType").asOpt[String].map {
              parentType => {
                implicit val user = request.user
                user match {
                  case Some(identity) => {
                    datasets.get(parentDatasetId) match {
                      case Some(parentDataset) => {
                        var folder: Folder = null
                        var displayName = name
                        // Avoid folders with the same name within a folder/dataset (parent). Check the name, and display name.
                        // And if it already exists, add a (x) with the corresponding number to the display name.
                        val countByName = folders.countByName(name, parentType, parentId)
                        if(countByName > 0) {
                          displayName = name + " (" + countByName + ")"
                        } else {
                          val countByDisplayName = folders.countByDisplayName(name, parentType, parentId)
                          if(countByDisplayName > 0) {
                            displayName = name + " (" + countByDisplayName + ")"
                          }
                        }

                        if(UUID(parentId) == parentDatasetId) {
                          folder = Folder(name = name.trim(), displayName = displayName.trim(), files = List.empty, folders = List.empty, parentId = UUID(parentId), parentType = parentType.toLowerCase(), parentDatasetId = parentDatasetId)
                        }  else if(parentType == "folder") {
                          folders.get(UUID(parentId)) match {
                            case Some(pfolder) => {
                              folder = Folder(name = name.trim(), displayName = displayName.trim(), files=List.empty, folders = List.empty, parentId = UUID(parentId), parentType = parentType.toLowerCase(), parentDatasetId = parentDatasetId)
                            }
                            case None => InternalServerError(s"parent folder $parentId not found")
                          }
                        } else {
                          InternalServerError(s"Parent type $parentType not acceptable")
                        }
                        Logger.debug(s"Saving folder $name")
                        if(folder != null){
                          folders.insert(folder)
                        }
                        if(parentType == "dataset" && UUID(parentId) == parentDatasetId) {
                          datasets.addFolder(parentDatasetId, folder.id)
                        } else if(parentType == "folder") {
                          folders.get(UUID(parentId)) match {
                            case Some(pfolder)=> folders.addSubFolder(pfolder.id, folder.id)
                            case None => InternalServerError(s"Parent folder $parentId not found")
                          }
                        }
                        val space = (request.body \ "currentSpace").asOpt[String]
                        //TODO: Add Created folder event
                        Ok(views.html.folders.listitem(folder, parentDataset.id) (request.user))
                      }
                      case None => InternalServerError(s"Parent Dataset $parentDatasetId not found")
                    }
                  }
                  case None => InternalServerError("User not found")
                }
              }
            }.getOrElse(BadRequest(toJson("Missing parameter [parentType]")))
          }
        }.getOrElse(BadRequest(toJson("Missing parameter [parentId]")))
      }
    }.getOrElse(BadRequest(toJson("Missing parameter [name]")))

  }


}
