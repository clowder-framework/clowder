package api

import java.util.Date

import controllers.Utils
import models.{Folder, ResourceRef}
import models.UUID
import javax.inject.{Inject, Singleton}
import play.api.libs.json.JsValue
import services._
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.Json._
import play.api.mvc.Request

import scala.collection.mutable.ListBuffer

/**
  * Folders are ways of organizing files within datasets. They can contain files and folders
 */
@Singleton
class Folders @Inject() (
  folders: FolderService,
  datasets: DatasetService,
  files: FileService,
  events: EventService,
  routing: ExtractorRoutingService) extends ApiController {

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
                        val trimname = name.trim()
                        var folder: Folder = null
                        var displayName = trimname
                        // Avoid folders with the same name within a folder/dataset (parent). Check the name, and display name.
                        // And if it already exists, add a (x) with the corresponding number to the display name.
                        val countByName = folders.countByName(trimname, parentType, parentId)
                        if(countByName > 0) {
                          val sameName: List[Folder] = folders.findByNameInParent(trimname, parentType, parentId)
                          val sameDisplayName: List[Folder] = folders.findByDisplayNameInParent(trimname, parentType, parentId)
                          val first = sameName.filter(_.displayName == trimname)
                          val firstDisplayName = sameDisplayName.filter(_.displayName == trimname)
                          if(first.isEmpty && firstDisplayName.isEmpty ){
                            displayName = trimname
                          } else {
                            var displayNameSet = false
                            for(i <- 1 until countByName.toInt + 1) {
                              val current = sameName.filter(_.displayName == trimname + " (" + i +  ")" )
                              if(current.isEmpty) {
                                displayName = trimname + " (" + i + ")"
                                displayNameSet = true
                              }
                            }
                            if(!displayNameSet) {
                              val index = countByName + sameDisplayName.length
                              displayName = trimname + " (" + index + ")"
                            }
                          }

                        } else {
                          val countByDisplayName = folders.countByDisplayName(trimname, parentType, parentId)
                          if(countByDisplayName > 0) {
                            val sameName: List[Folder] = folders.findByDisplayNameInParent(trimname, parentType, parentId)
                            val first = sameName.filter(_.displayName == trimname)
                            if(first.isEmpty) {
                              displayName = trimname
                            } else {
                              var displayNameSet = false
                              for(i <- 1 until countByName.toInt + 1) {
                                val current = sameName.filter(_.displayName == trimname + " (" + i +  ")" )
                                if(current.isEmpty) {
                                  displayName = trimname + " (" + i + ")"
                                  displayNameSet = true
                                }
                              }
                              if(!displayNameSet) {
                                displayName = trimname + " (" + countByDisplayName + ")"
                              }
                            }

                          }
                        }

                        if(UUID(parentId) == parentDatasetId) {
                          folder = Folder(author= identity.getMiniUser, created = new Date(), name = trimname, displayName = displayName.trim(), files = List.empty, folders = List.empty, parentId = UUID(parentId), parentType = parentType.toLowerCase(), parentDatasetId = parentDatasetId)
                        }  else if(parentType == "folder") {
                          folders.get(UUID(parentId)) match {
                            case Some(pfolder) => {
                              folder = Folder(author= identity.getMiniUser, created = new Date(), name = trimname, displayName = displayName.trim(), files=List.empty, folders = List.empty, parentId = UUID(parentId), parentType = parentType.toLowerCase(), parentDatasetId = parentDatasetId)
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

                        events.addObjectEvent(request.user, parentDatasetId, parentDataset.name, "added_folder")
                        Ok(folderToJson(folder))
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

  private def folderToJson(folder: Folder) = {
    toJson(Map(
      "id" -> folder.id.stringify,
      "name" -> folder.name,
      "parentDatasetID" -> folder.parentDatasetId.stringify,
      "parentId" -> folder.parentId.stringify,
      "parentType" -> folder.parentType,
      "files" -> folder.files.mkString(","),
      "folders" -> folder.folders.mkString(","),
      "created" -> folder.created.toString
    ))
  }

  /**
    * traverse subfiles directly/indirectly under this folder and send file deletion event to rabbitmq.
    * @param folder a Folder object
    * @param host Clowder host
    * @param requestAPIKey user apikey
    */
  def subfilesDeletionRabbitmqNotification(folderId: UUID, host: String, requestAPIKey: Option[String]): Unit = {
    Logger.debug(s"[subfilesDeletionRabbitmqNotification] under folderid $folderId")
    folders.get(folderId) match {
      case Some(folder) => {
        files.get(folder.files).found.foreach(file => {
          // notify rabbitmq
          Logger.debug(s"[subfilesDeletionRabbitmqNotification] rabbitmq delete event of fileid ${file.id} under folderid $folderId")
          datasets.findByFileIdAllContain(file.id).foreach { ds =>
            routing.fileRemovedFromDataset(file, ds, host, requestAPIKey)
          }
        })
        folder.folders.map {
          // Don't need to do a get here since it's done first thing in the recursive call
          subfolderId => subfilesDeletionRabbitmqNotification(subfolderId, host, requestAPIKey)
        }
      }
      case None => Logger.warn(s"[subfilesDeletionRabbitmqNotification] folder: $folderId not exist!")
    }

  }

  /**
    * Delete a folder and all its children (files and folders).
    *
    * FIXME: Messages go out on RabbitMQ and then files are deleted. If an extractor needs a file that has just been
    * deleted it might not find it (depending on the length of the queue. This is not the only place where this can happen
    * and it's a common issue with delete RabbitMQ messages.
    *
    * @param parentDatasetId
    * @param folderId
    * @return
    */
  def deleteFolder(parentDatasetId: UUID, folderId: UUID) =
    PermissionAction(Permission.RemoveResourceFromDataset, Some(ResourceRef(ResourceRef.dataset, parentDatasetId))) { implicit request =>
    Logger.debug("--- Api Deleting Folder ---")
    datasets.get(parentDatasetId) match {
      case Some(parentDataset) => {
        folders.get(folderId) match {
          case Some(folder) => {

            // 1. traverse subfiles directly/indirectly under this folder and send file deletion event to rabbitmq.
            subfilesDeletionRabbitmqNotification(folderId, Utils.baseUrl(request), request.apiKey)

            events.addObjectEvent(request.user, parentDatasetId, parentDataset.name, "deleted_folder")
            // 2. delete folders and files from Clowder.
            folders.delete(folderId, Utils.baseUrl(request), request.apiKey, request.user)
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
              var countByName = folders.countByName(name, folder.parentType, folder.parentId.stringify)
              if(name == folder.name) {
                countByName -=1
              }
              if(countByName > 0) {
                displayName = name + " (" + countByName + ")"
              } else {
                val countByDisplayName = folders.countByDisplayName(name, folder.parentType, folder.parentId.stringify)
                if(countByDisplayName > 0) {
                  displayName = name + " (" + countByDisplayName + ")"
                }
              }

              folders.updateName(folder.id, name, displayName)
              events.addObjectEvent(request.user, parentDatasetId, parentDataset.name, "updated_folder")
              Ok(toJson(Map("status" -> "success", "newname" -> displayName)))

            }
            case None => InternalServerError(s"Folder $folderId not found")
          }
       }
       case None => InternalServerError(s"Parent dataset $parentDatasetId not found")
     }
  }

  def getAllFoldersByDatasetId(datasetId: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    implicit val user = request.user
    val response = ListBuffer.empty[JsValue]
    val foldersList = folders.findByParentDatasetId(datasetId)
    foldersList.map{ folder =>
      var folderHierarchy = new ListBuffer[String]()
      folderHierarchy += folder.displayName
      var f1: Folder = folder
      while(f1.parentType == "folder") {
        folders.get(f1.parentId) match {
          case Some(fparent) => {
            folderHierarchy += fparent.displayName
            f1 = fparent
          }
          case None =>
        }
      }
      folderHierarchy +=""
      response += toJson(Map("id" -> folder.id.stringify, "name" -> folderHierarchy.reverse.mkString("/")))

    }
    Ok(toJson(response))
  }

  def moveFileBetweenFolders(datasetId: UUID, newFolderId: UUID, fileId: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, datasetId)))(parse.json) { implicit request =>
    implicit val user = request.user
    datasets.get(datasetId) match {
      case Some(dataset) => {
        val oldFolderId = (request.body \ "folderId").asOpt[String]

        folders.get(newFolderId) match {
          case Some(newFolder) => {
            files.get(fileId) match {
              case Some(file) => {
                if(newFolder.parentDatasetId == datasetId) {

                  oldFolderId match {
                    case Some(id) => {
                      folders.get(UUID(id)) match {
                        case Some(oldFolder) => {
                          if(oldFolder.files.contains(fileId)) {
                            folders.removeFile(oldFolder.id, fileId)
                            folders.addFile(newFolder.id, fileId)
                            files.index(fileId)
                            datasets.index(datasetId)
                            Ok(toJson(Map("status" -> "success", "fileName" -> file.filename, "folderName" -> newFolder.name)))
                          } else {
                            BadRequest("Failed to move file. The file with id: " + file.id.stringify + " isn't in folder with id: " + oldFolder.id.stringify  )
                          }

                        }
                        case None => BadRequest("Failed to move file with id: " + file.id.stringify + " from folder with id: " + oldFolderId + ". The folder doesn't exist")
                      }
                    }
                    case None => {
                      if(dataset.files.contains(fileId)) {
                        folders.addFile(newFolder.id, fileId)
                        datasets.removeFile(datasetId, fileId)
                        files.index(fileId)
                        datasets.index(datasetId)
                        Ok(toJson(Map("status" -> "success", "fileName" -> file.filename, "folderName" -> newFolder.name)))
                      } else {
                        BadRequest("Failed to move file. The file with id: " + file.id.stringify + "Isn't in dataset with id: " + dataset.id.stringify  )
                      }

                    }
                  }


                } else {
                  BadRequest("Failed to copy file. The destination folder is not in the dataset.")
                }

              }
              case None => BadRequest("Failed to copy file. There is no file with id:  " + fileId.stringify)
            }

          }
          case None => BadRequest("Failed to copy the file. The destination folder doesn't exist. New folder Id: "+ newFolderId)
        }

      }
      case None => BadRequest("There is no dataset with id: " + datasetId.stringify)
    }

  }

  def moveFileToDataset(datasetId: UUID, oldFolderId: UUID, fileId: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    implicit val user = request.user
    datasets.get(datasetId) match {
      case Some(dataset) => {
        files.get(fileId) match {
          case Some(file) => {
            folders.get(oldFolderId) match {
              case Some(folder) => {
                if(folder.files.contains(fileId)) {
                  datasets.addFile(datasetId, file)
                  folders.removeFile(oldFolderId, fileId)
                  files.index(fileId)
                  datasets.index(datasetId)
                  Ok(toJson(Map("status" -> "success", "fileName"-> file.filename )))
                } else {
                  BadRequest("The file you are trying to move isn't in the folder you are moving it from.")
                }
              }
              case None => BadRequest("Failed to copy the file. The ")
            }

          }
          case None => BadRequest("Failure to copy the file. There is no file with id: " + fileId.stringify)
        }
      }
      case None => BadRequest("There is no dataset with id: " + datasetId.stringify)
    }
  }

}
