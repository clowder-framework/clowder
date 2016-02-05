package api

import models.{ResourceRef, Folder}
import models.UUID
import javax.inject.{Inject, Singleton}
import com.wordnik.swagger.annotations.Api
import play.libs.Json
import services._
import play.api.Logger
import play.api.libs.json.Json._
/**
 */
@Api(value= "/folders", listingPath ="/api-docs.json/folders", description ="A folder is a container of files and other folders")
@Singleton
class Folders @Inject() (
  folders: FolderService,
  datasets: DatasetService) extends ApiController {

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

                        //TODO: Add Created folder event
                        Ok(toJson(Map("status" -> "success")))
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

  def deleteFolder(parentDatasetId: UUID, folderId: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, parentDatasetId))) { implicit request =>
    Logger.debug("--- Api Deleting Folder ---")
    datasets.get(parentDatasetId) match {
      case Some(parentDataset) => {
        folders.get(folderId) match {
          case Some(folder) => {
            //TODO: Add Deleted folder event? Make everyone unfollow the folder. Add followers field to the model?
            folders.delete(folderId)
            if(folder.parentType == "dataset") {
              datasets.removeFolder(parentDatasetId, folderId)
            } else if(folder.parentType == "folder") {
              folders.get(folder.parentId) match {
                case Some(parentFolder) => folders.removeSubFolder(parentFolder.id, folder.id)
                case None =>
              }
            }
            Ok(toJson(Map("status" -> "success", "folderId" -> folderId.stringify)))
          }
          case None => InternalServerError(s"Folder $folderId not found")
        }
      }
      case None => InternalServerError(s"Parent Dataset $parentDatasetId not found")
    }

  }

   def updateFolderName(parentDatasetId: UUID, folderId: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, parentDatasetId)))(parse.json) { implicit request =>
    implicit val user = request.user
     datasets.get(parentDatasetId) match {
       case Some(parentDataset) => {
          folders.get(folderId) match {
            case Some(folder) => {
              var name: String = null
              val aResult = (request.body \ "name").asOpt[String]

              aResult match {
                case Some(s) => name = s
                case None => BadRequest(toJson("Name is missing"))
              }
              Logger.debug(s"Update information for folder with id $folderId. New name is $name")
              var displayName = name
              // Avoid folders with the same name within a folder/dataset (parent). Check the name, and display name.
              // And if it already exists, add a (x) with the corresponding number to the display name.
              val countByName = folders.countByName(name, folder.parentType, folder.parentId.stringify)
              if(countByName > 0) {
                displayName = name + " (" + countByName + ")"
              } else {
                val countByDisplayName = folders.countByDisplayName(name, folder.parentType, folder.parentId.stringify)
                if(countByDisplayName > 0) {
                  displayName = name + " (" + countByDisplayName + ")"
                }
              }

              folders.updateName(folder.id, name, displayName)
              //TODO: Add Update event
              Ok(toJson(Map("status" -> "success", "newname" -> displayName)))

            }
            case None => InternalServerError(s"Folder $folderId not found")
          }
       }
       case None => InternalServerError(s"Parent dataset $parentDatasetId not found")
     }
   }

}
