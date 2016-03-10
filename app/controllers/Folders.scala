package controllers

import api.Permission
import models.{Folder, ResourceRef, UUID}
import play.api.Logger
import play.api.libs.json.Json._
import services._
import javax.inject.Inject
import java.util.Date

/**
  * * Folders are ways of organizing files within datasets. They can contain files and folders
  */
class Folders @Inject()(datasets: DatasetService, folders: FolderService, events: EventService) extends SecuredController{

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
    Logger.debug("--- Creating new folder ---- ")
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
