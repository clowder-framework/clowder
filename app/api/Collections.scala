package api

import java.io.{ByteArrayInputStream, InputStream, ByteArrayOutputStream}
import java.security.{DigestInputStream, MessageDigest}
import java.text.SimpleDateFormat
import java.util.zip.{ZipEntry, ZipOutputStream, Deflater}

import Iterators.RootCollectionIterator
import _root_.util.JSONLD
import api.Permission.Permission
import org.apache.commons.codec.binary.Hex
import play.api.Logger
import play.api.Play.current
import models._
import play.api.libs.iteratee.Enumerator
import services._
import play.api.libs.json._
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json.toJson
import javax.inject.{ Singleton, Inject}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.concurrent.Execution.Implicits._
import scala.util.parsing.json.JSONArray
import scala.util.{Try, Success, Failure}
import java.util.{Calendar, Date}
import controllers.Utils

import scala.collection.immutable.List


/**
 * Manipulate collections.
 */
@Singleton
class Collections @Inject() (datasets: DatasetService,
                             collections: CollectionService,
                             previews: PreviewService,
                             userService: UserService,
                             events: EventService,
                             spaces:SpaceService,
                             appConfig: AppConfigurationService,
                             folders : FolderService,
                             files: FileService,
                             metadataService : MetadataService) extends ApiController {

  def createCollection() = PermissionAction(Permission.CreateCollection) (parse.json) { implicit request =>
    Logger.debug("Creating new collection")
    (request.body \ "name").asOpt[String].map { name =>

      var c : Collection = null
      implicit val user = request.user
      user match {
        case Some(identity) => {
          val description = (request.body \ "description").asOpt[String].getOrElse("")
          (request.body \ "space").asOpt[String] match {
            case None | Some("default") => c = Collection(name = name, description = description, created = new Date(), datasetCount = 0, author = identity)
            case Some(space) =>  if (spaces.get(UUID(space)).isDefined) {

              c = Collection(name = name, description = description, created = new Date(), datasetCount = 0, author = identity, spaces = List(UUID(space)))
            } else {
              BadRequest(toJson("Bad space = " + space))
            }
          }

          collections.insert(c) match {
            case Some(id) => {
              appConfig.incrementCount('collections, 1)
              c.spaces.map(spaceId => spaces.get(spaceId)).flatten.map{ s =>
                spaces.addCollection(c.id, s.id, user)
                collections.addToRootSpaces(c.id, s.id)
                events.addSourceEvent(request.user, c.id, c.name, s.id, s.name, "add_collection_space")
              }
              Ok(toJson(Map("id" -> id)))
            }
            case None => Ok(toJson(Map("status" -> "error")))
          }
        }
        case None => InternalServerError("User Not found")
      }
    }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }

  def attachDataset(collectionId: UUID, datasetId: UUID) = PermissionAction(Permission.AddResourceToCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>

    collections.addDataset(collectionId, datasetId) match {
      case Success(_) => {
        var datasetsInCollection = 0
        collections.get(collectionId) match {
          case Some(collection) => {
            datasets.get(datasetId) match {
              case Some(dataset) => {
                if (play.Play.application().configuration().getBoolean("addDatasetToCollectionSpace")){
                  collections.addDatasetToCollectionSpaces(collection.id,dataset.id, request.user)
                }
                events.addSourceEvent(request.user , dataset.id, dataset.name, collection.id, collection.name, "attach_dataset_collection")
              }
              case None =>
            }
            datasetsInCollection = collection.datasetCount
          }
          case None =>
        }
        //datasetsInCollection is the number of datasets in this collection
        Ok(Json.obj("datasetsInCollection" -> Json.toJson(datasetsInCollection) ))
      }
      case Failure(t) => InternalServerError
    }

  }

  /**
   * Reindex the given collection, if recursive is set to true it will
   * also reindex all datasets and files.
   */
  def reindex(id: UUID, recursive: Boolean) = PermissionAction(Permission.CreateCollection, Some(ResourceRef(ResourceRef.collection, id))) {  implicit request =>
      collections.get(id) match {
        case Some(coll) => {
          current.plugin[ElasticsearchPlugin].foreach {
            _.index(coll, recursive)
          }
          Ok(toJson(Map("status" -> "success")))
        }
        case None => {
          Logger.error("Error getting collection" + id)
          BadRequest(toJson(s"The given collection id $id is not a valid ObjectId."))
        }
      }
  }

  def removeDataset(collectionId: UUID, datasetId: UUID, ignoreNotFound: String) = PermissionAction(Permission.RemoveResourceFromCollection, Some(ResourceRef(ResourceRef.collection, collectionId)), Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    collections.removeDataset(collectionId, datasetId, Try(ignoreNotFound.toBoolean).getOrElse(true)) match {
      case Success(_) => {

        collections.get(collectionId) match {
        case Some(collection) => {
          datasets.get(datasetId) match {
            case Some(dataset) => {
              events.addSourceEvent(request.user , dataset.id, dataset.name, collection.id, collection.name, "remove_dataset_collection")
            }
            case None =>
          }
        }
        case None =>
      }
      Ok(toJson(Map("status" -> "success")))
    }
    case Failure(t) => {
      Logger.error("Error: " + t)
      InternalServerError
    }
    }
  }

  def removeCollection(collectionId: UUID) = PermissionAction(Permission.DeleteCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val useTrash = play.api.Play.configuration.getBoolean("useTrash").getOrElse(false)
        if (!useTrash || (useTrash && collection.trash)){
          events.addObjectEvent(request.user , collection.id, collection.name, "delete_collection")
          collections.delete(collectionId)
          appConfig.incrementCount('collections, -1)
          current.plugin[AdminsNotifierPlugin].foreach {
            _.sendAdminsNotification(Utils.baseUrl(request),"Collection","removed",collection.id.stringify, collection.name)
          }
        } else {
          collections.addToTrash(collectionId, Some(new Date()))
          events.addObjectEvent(request.user, collectionId, collection.name, "move_collection_trash")
          Ok(toJson(Map("status" -> "success")))
        }
      }
    }
    //Success anyway, as if collection is not found it is most probably deleted already
    Ok(toJson(Map("status" -> "success")))
  }

  def restoreCollection(collectionId : UUID) = PermissionAction(Permission.DeleteCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) {implicit request=>
    implicit val user = request.user
    user match {
      case Some(u) => {
        collections.get(collectionId) match {
          case Some(col) => {
            collections.restoreFromTrash(collectionId, None)
            events.addObjectEvent(user, collectionId, col.name, "restore_collection_trash")
            Ok(toJson(Map("status" -> "success")))
          }
          case None => InternalServerError("Update Access failed")
        }
      }
      case None => BadRequest("No user supplied")
    }
  }

  def listCollectionsInTrash(limit : Int) = PrivateServerAction {implicit request =>
    val trash_collections_list = request.user match {
      case Some(usr) => {
        for (collection <- collections.listUserTrash(request.user,limit))
          yield jsonCollection(collection)
      }
      case None => List.empty
    }
    Ok(toJson(trash_collections_list))
  }

  def emptyTrash() = PrivateServerAction {implicit request =>
    val user = request.user
    user match {
      case Some(u) => {
        val trashcollections = collections.listUserTrash(request.user,0)
        for (collection <- trashcollections){
          events.addObjectEvent(request.user , collection.id, collection.name, "delete_collection")
          collections.delete(collection.id)
          appConfig.incrementCount('collections, -1)
          current.plugin[AdminsNotifierPlugin].foreach {
            _.sendAdminsNotification(Utils.baseUrl(request),"Collection","removed",collection.id.stringify, collection.name)
          }
        }
      }
      case None =>
    }
    Ok(toJson("Done emptying trash"))
  }

  def clearOldCollectionsTrash(days : Int) = ServerAdminAction {implicit request =>

    val deleteBeforeCalendar : Calendar = Calendar.getInstance()
    deleteBeforeCalendar.add(Calendar.DATE,-days)

    val deleteBeforeDateTime = deleteBeforeCalendar.getTimeInMillis()
    val user = request.user
    user match {
      case Some(u) => {
        val allCollectionsTrash = collections.listUserTrash(None,0)
        allCollectionsTrash.foreach( c => {
          val dateInTrash = c.dateMovedToTrash.getOrElse(new Date())
          if (dateInTrash.getTime() < deleteBeforeDateTime) {
            events.addObjectEvent(request.user , c.id, c.name, "delete_collection")
            collections.delete(c.id)
            appConfig.incrementCount('collections, -1)
            current.plugin[AdminsNotifierPlugin].foreach {
              _.sendAdminsNotification(Utils.baseUrl(request),"Collection","removed",c.id.stringify, c.name)
            }
          }
        })
        Ok(toJson("Deleted all collections in trash older than " + days + " days"))
      }
      case None => BadRequest("No user supplied")
    }
  }

  def list(title: Option[String], date: Option[String], limit: Int, exact: Boolean) = PrivateServerAction { implicit request =>
    Ok(toJson(listCollections(title, date, limit, Set[Permission](Permission.ViewCollection), false, request.user, request.user.fold(false)(_.superAdminMode), exact)))
  }

  def listCanEdit(title: Option[String], date: Option[String], limit: Int, exact: Boolean) = PrivateServerAction { implicit request =>
    Ok(toJson(listCollections(title, date, limit, Set[Permission](Permission.AddResourceToCollection, Permission.EditCollection), false, request.user, request.user.fold(false)(_.superAdminMode), exact)))
  }

  def addDatasetToCollectionOptions(datasetId: UUID, title: Option[String], date: Option[String], limit: Int, exact: Boolean) = PrivateServerAction { implicit request =>
    implicit val user = request.user
    var listAll = false
    var collectionList: List[Collection] = List.empty
    if(play.api.Play.current.plugin[services.SpaceSharingPlugin].isDefined) {
      listAll = true
    } else {
      datasets.get(datasetId) match {
        case Some(dataset) => {
          if(dataset.spaces.length > 0) {
            collectionList = collections.listInSpaceList(title, date, limit, dataset.spaces, Set[Permission](Permission.AddResourceToCollection, Permission.EditCollection), user)
          } else {
            listAll = true
          }

        }
        case None => Logger.debug("The dataset was not found")
      }
    }
    if(listAll) {
      collectionList = listCollections(title, date, limit, Set[Permission](Permission.AddResourceToCollection, Permission.EditCollection), false, request.user, request.user.fold(false)(_.superAdminMode), exact)
    }
    Ok(toJson(collectionList))
  }

  private def getNextLevelCollections(current_collections : List[Collection]) : List[Collection] = {
    val next_level_collections : ListBuffer[Collection] = ListBuffer.empty[Collection]
    for (current_collection : Collection <- current_collections){
      for (child_id <- current_collection.child_collection_ids){
        collections.get(child_id) match {
          case Some(child_col) => next_level_collections += child_col
          case None =>
        }
      }
    }
    next_level_collections.toList
  }



  def listPossibleParents(currentCollectionId : String, title: Option[String], date: Option[String], limit: Int, exact: Boolean) = PrivateServerAction { implicit request =>
    val selfAndAncestors = collections.getSelfAndAncestors(UUID(currentCollectionId))
    val descendants = collections.getAllDescendants(UUID(currentCollectionId)).toList
    val allCollections = listCollections(title, date, limit, Set[Permission](Permission.AddResourceToCollection, Permission.EditCollection), false,
      request.user, request.user.fold(false)(_.superAdminMode), exact)
    val possibleNewParents = allCollections.filter((c: Collection) =>
      if(play.api.Play.current.plugin[services.SpaceSharingPlugin].isDefined) {
        (!selfAndAncestors.contains(c) && !descendants.contains(c))
      } else {
            collections.get(UUID(currentCollectionId)) match {
              case Some(coll) => {
                if(coll.spaces.length == 0) {
                   (!selfAndAncestors.contains(c) && !descendants.contains(c))

                } else {
                   (!selfAndAncestors.contains(c) && !descendants.contains(c) && c.spaces.intersect(coll.spaces).length > 0)
                }
              }
              case None => (!selfAndAncestors.contains(c) && !descendants.contains(c))
            }
      }
    )
    Ok(toJson(possibleNewParents))
  }



  /**
   * Returns list of collections based on parameters and permissions.
   * TODO this needs to be cleaned up when do permissions for adding to a resource
   */
  private def listCollections(title: Option[String], date: Option[String], limit: Int, permission: Set[Permission], mine: Boolean, user: Option[User], superAdmin: Boolean, exact: Boolean) : List[Collection] = {
    if (mine && user.isEmpty) return List.empty[Collection]

    (title, date) match {
      case (Some(t), Some(d)) => {
        if (mine)
          collections.listUser(d, true, limit, t, user, superAdmin, user.get, exact)
        else
          collections.listAccess(d, true, limit, t, permission, user, superAdmin, true,false, exact)
      }
      case (Some(t), None) => {
        if (mine)
          collections.listUser(limit, t, user, superAdmin, user.get, exact)
        else
          collections.listAccess(limit, t, permission, user, superAdmin, true,false, exact)
      }
      case (None, Some(d)) => {
        if (mine)
          collections.listUser(d, true, limit, user, superAdmin, user.get)
        else
          collections.listAccess(d, true, limit, permission, user, superAdmin, true,false)
      }
       case (None, None) => {
        if (mine)
          collections.listUser(limit, user, superAdmin, user.get)
        else
          collections.listAccess(limit, permission, user, superAdmin, true,false)
      }
    }
  }

  def getCollection(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    collections.get(collectionId) match {
      case Some(x) => Ok(jsonCollection(x))
      case None => BadRequest(toJson("collection not found"))
    }
  }

  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description,
      "created" -> collection.created.toString,"author"-> collection.author.toString, "root_flag" -> collections.hasRoot(collection).toString,
      "child_collection_ids"-> collection.child_collection_ids.toString, "parent_collection_ids" -> collection.parent_collection_ids.toString,
    "childCollectionsCount" -> collection.childCollectionsCount.toString, "datasetCount"-> collection.datasetCount.toString, "spaces" -> collection.spaces.toString))
  }

  def updateCollectionName(id: UUID) = PermissionAction(Permission.EditCollection, Some(ResourceRef(ResourceRef.collection, id)))(parse.json) {
    implicit request =>
      implicit val user = request.user
      if (UUID.isValid(id.stringify)) {
        var name: String = null
        val aResult = (request.body \ "name").validate[String]
        aResult match {
          case s: JsSuccess[String] => {
            name = s.get
          }
          case e: JsError => {
            Logger.error("Errors: " + JsError.toFlatJson(e).toString())
            BadRequest(toJson(s"name data is missing"))
          }
        }
        Logger.debug(s"Update title for collection with id $id. New name: $name")
        collections.updateName(id, name)
        collections.get(id) match {
          case Some(collection) => {
            events.addObjectEvent(user, id, collection.name, "update_collection_information")
          }

        }
        collections.index(Some(id))
        Ok(Json.obj("status" -> "success"))
      }
      else {
        Logger.error(s"The given id $id is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }
  }

  def updateCollectionDescription(id: UUID) = PermissionAction(Permission.EditCollection, Some(ResourceRef(ResourceRef.collection, id)))(parse.json) {
    implicit request =>
      implicit val user = request.user
      if (UUID.isValid(id.stringify)) {
        var description: String = null
        val aResult = (request.body \ "description").validate[String]
        aResult match {
          case s: JsSuccess[String] => {
            description = s.get
          }
          case e: JsError => {
            Logger.error("Errors: " + JsError.toFlatJson(e).toString())
            BadRequest(toJson(s"description data is missing"))
          }
        }
        Logger.debug(s"Update description for collection with id $id. New description: $description")
        collections.updateDescription(id, description)
        collections.get(id) match {
          case Some(collection) => {
            events.addObjectEvent(user, id, collection.name, "update_collection_information")
          }

        }
        collections.index(Some(id))
        Ok(Json.obj("status" -> "success"))
      }
      else {
        Logger.error(s"The given id $id is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }

  }
  /**
   * Add preview to file.
   */
  def attachPreview(collection_id: UUID, preview_id: UUID) = PermissionAction(Permission.EditCollection, Some(ResourceRef(ResourceRef.collection, collection_id)))(parse.json) { implicit request =>
      // Use the "extractor_id" field contained in the POST data.  Use "Other" if absent.
      val eid = (request.body \ "extractor_id").asOpt[String]
      val extractor_id = if (eid.isDefined) {
        eid
      } else {
        Logger.debug("api.Files.attachPreview(): No \"extractor_id\" specified in request, set it to None.  request.body: " + request.body.toString)
        Some("Other")
      }
      val preview_type = (request.body \ "preview_type").asOpt[String].getOrElse("")
      request.body match {
        case JsObject(fields) => {
          collections.get(collection_id) match {
            case Some(collection) => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  // "extractor_id" is stored at the top level of "Preview".  Remove it from the "metadata" field to avoid dup.
                  // TODO replace null with None
                  previews.attachToCollection(preview_id, collection_id, preview_type, extractor_id, request.body)
                  Ok(toJson(Map("status" -> "success")))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
            //If file to be previewed is not found, just delete the preview
            case None => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  Logger.debug("Collection not found. Deleting previews.files " + preview_id)
                  previews.removePreview(preview)
                  BadRequest(toJson("Collection not found. Preview deleted."))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
          }
        }
        case _ => Ok("received something else: " + request.body + '\n')
      }
  }

  def follow(id: UUID) = AuthenticatedAction { implicit request =>
      implicit val user = request.user

      user match {
        case Some(loggedInUser) => {
          collections.get(id) match {
            case Some(collection) => {
              events.addObjectEvent(user, id, collection.name, "follow_collection")
              collections.addFollower(id, loggedInUser.id)
              userService.followCollection(loggedInUser.id, id)

              val recommendations = getTopRecommendations(id, loggedInUser)
              recommendations match {
                case x::xs => Ok(Json.obj("status" -> "success", "recommendations" -> recommendations))
                case Nil => Ok(Json.obj("status" -> "success"))
              }
            }
            case None => {
              NotFound
            }
          }
        }
        case None => {
          Unauthorized
        }
      }
  }

  def unfollow(id: UUID) = AuthenticatedAction { implicit request =>
      implicit val user = request.user

      user match {
        case Some(loggedInUser) => {
          collections.get(id) match {
            case Some(collection) => {
              events.addObjectEvent(user, id, collection.name, "unfollow_collection")
              collections.removeFollower(id, loggedInUser.id)
              userService.unfollowCollection(loggedInUser.id, id)
              Ok
            }
            case None => {
              NotFound
            }
          }
        }
        case None => {
          Unauthorized
        }
      }
  }

  def getTopRecommendations(followeeUUID: UUID, follower: User): List[MiniEntity] = {
    val followeeModel = collections.get(followeeUUID)
    followeeModel match {
      case Some(followeeModel) => {
        val sourceFollowerIDs = followeeModel.followers
        val excludeIDs = follower.followedEntities.map(typedId => typedId.id) ::: List(followeeUUID, follower.id)
        val num = play.api.Play.configuration.getInt("number_of_recommendations").getOrElse(10)
        userService.getTopRecommendations(sourceFollowerIDs, excludeIDs, num)
      }
      case None => {
        List.empty
      }
    }
  }


  def attachSubCollection(collectionId: UUID, subCollectionId: UUID) = PermissionAction(Permission.AddResourceToCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    collections.addSubCollection(collectionId, subCollectionId, request.user) match {
      case Success(_) => {
        collections.get(collectionId) match {
          case Some(collection) => {
            collections.get(subCollectionId) match {
              case Some(sub_collection) => {
                events.addSourceEvent(request.user, sub_collection.id, sub_collection.name, collection.id, collection.name, "add_sub_collection")
                Ok(jsonCollection(collection))
              }
            }
          }
        }
      }
      case Failure(t) => InternalServerError
    }
  }



  def createCollectionWithParent() = PermissionAction(Permission.CreateCollection) (parse.json) { implicit request =>
    (request.body \ "name").asOpt[String].map{ name =>
      var c : Collection = null
      implicit val user = request.user

      user match {
        case Some(identity) => {
          val description = (request.body \ "description").asOpt[String].getOrElse("")
          (request.body \ "space").asOpt[String] match {
            case None | Some("default") =>  c = Collection(name = name, description = description, created = new Date(), datasetCount = 0, childCollectionsCount = 0, author = identity, root_spaces = List.empty)
            case Some(space) => if (spaces.get(UUID(space)).isDefined) {
              c = Collection(name = name, description = description, created = new Date(), datasetCount = 0, author = identity, spaces = List(UUID(space)), root_spaces = List(UUID(space)))
            } else {
              BadRequest(toJson("Bad space = " + space))
            }
          }

          collections.insert(c) match {
            case Some(id) => {
              appConfig.incrementCount('collections, 1)
              c.spaces.map{ spaceId =>
                spaces.get(spaceId)}.flatten.map{ s =>
                  spaces.addCollection(c.id, s.id, request.user)
                  collections.addToRootSpaces(c.id, s.id)
                  events.addSourceEvent(request.user, c.id, c.name, s.id, s.name, "add_collection_space")
              }

              //do stuff with parent here
              (request.body \"parentId").asOpt[String] match {
                case Some(parentId) => {
                  collections.get(UUID(parentId)) match {
                    case Some(parentCollection) => {
                      collections.addSubCollection(UUID(parentId), UUID(id), user) match {
                        case Success(_) => {
                          Ok(toJson(Map("id" -> id)))
                        }
                      }
                    }
                    case None => {
                      Ok(toJson("No collection with parentId found"))
                    }
                  }
                }
                case None => {
                  Ok(toJson("No parentId supplied"))
                }

              }
              Ok(toJson(Map("id" -> id)))
            }
            case None => Ok(toJson(Map("status" -> "error")))
          }
        }
        case None => InternalServerError("User Not found")
      }

    }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }

  def removeSubCollection(collectionId: UUID, subCollectionId: UUID, ignoreNotFound: String) = PermissionAction(Permission.RemoveResourceFromCollection, Some(ResourceRef(ResourceRef.collection, collectionId)), Some(ResourceRef(ResourceRef.collection, subCollectionId))) { implicit request =>

    collections.removeSubCollection(collectionId, subCollectionId, Try(ignoreNotFound.toBoolean).getOrElse(true)) match {
      case Success(_) => {

        collections.get(collectionId) match {
          case Some(collection) => {
            collections.get(subCollectionId) match {
              case Some(sub_collection) => {
                events.addSourceEvent(request.user, sub_collection.id, sub_collection.name, collection.id, collection.name, "remove_subcollection")
              }
            }
          }
        }
        Ok(toJson(Map("status" -> "success")))
      }
      case Failure(t) => InternalServerError
    }
  }

  def isCollectionRootOrHasNoParent(collectionId: UUID): Unit = {
    collections.get(collectionId) match {
      case Some(collection) => {
        if (collections.hasRoot(collection) || collection.parent_collection_ids.isEmpty) {
          return true
        } else
          return false

      }
      case None =>
        Ok("no collection with id : " + collectionId)
    }
  }


  /**
    * Adds a Root flag for a collection in a space
    */
  def setRootSpace(collectionId: UUID, spaceId: UUID)  = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    Logger.debug("changing the value of the root flag")
    (collections.get(collectionId), spaces.get(spaceId)) match {
      case (Some(collection), Some(space)) => {
        spaces.addCollection(collectionId, spaceId, request.user)
        collections.addToRootSpaces(collectionId, spaceId)
        events.addSourceEvent(request.user, collection.id, collection.name, space.id, space.name, "add_collection_space")
        Ok(jsonCollection(collection))
      } case (None, _) => {
        Logger.error("Error getting collection  " + collectionId)
        BadRequest(toJson(s"The given collection id $collectionId is not a valid ObjectId."))
      }
      case _ => {
        Logger.error("Error getting space  " + spaceId)
        BadRequest(toJson(s"The given space id $spaceId is not a valid ObjectId."))
      }
    }
  }

  /**
    * Remove root flag from a collection in a space
    */
  def unsetRootSpace(collectionId: UUID, spaceId: UUID)  = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    Logger.debug("changing the value of the root flag")
    collections.get(collectionId) match {
      case Some(collection) => {
        collections.removeFromRootSpaces(collectionId, spaceId)
        Ok(jsonCollection(collection))
      } case None => {
        Logger.error("Error getting collection  " + collectionId)
        BadRequest(toJson(s"The given collection id $collectionId is not a valid ObjectId."))
      }
    }
  }

  def getRootCollections() = PermissionAction(Permission.ViewCollection) { implicit request =>
    val root_collections_list = for (collection <- collections.listAccess(100,Set[Permission](Permission.ViewCollection),request.user,true, true,false); if collections.hasRoot(collection)  )
      yield jsonCollection(collection)

    Ok(toJson(root_collections_list))
  }

  def getAllCollections(limit : Int, showAll: Boolean) = PermissionAction(Permission.ViewCollection) { implicit request =>
    val all_collections_list = request.user match {
      case Some(usr) => {
        for (collection <- collections.listAllCollections(usr, showAll, limit))
          yield jsonCollection(collection)
      }
      case None => List.empty
    }
    Ok(toJson(all_collections_list))
  }



  def getTopLevelCollections() = PermissionAction(Permission.ViewCollection){ implicit request =>
    implicit val user = request.user
    val count = collections.countAccess(Set[Permission](Permission.ViewCollection),user,true)
    val limit = count.toInt
    val top_level_collections = for (collection <- collections.listAccess(limit,Set[Permission](Permission.ViewCollection),request.user,true, true,false); if (collections.hasRoot(collection) || collection.parent_collection_ids.isEmpty))
      yield jsonCollection(collection)
    Ok(toJson(top_level_collections))
  }

  def getChildCollectionIds(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection,collectionId))){implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val childCollectionIds = collection.child_collection_ids
        Ok(toJson(childCollectionIds))
      }
      case None => BadRequest(toJson("collection not found"))
    }
  }

  def getParentCollectionIds(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection,collectionId))){implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val parentCollectionIds = collection.parent_collection_ids
        Ok(toJson(parentCollectionIds))
      }
      case None => BadRequest(toJson("collection not found"))
    }
  }



  def getChildCollections(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection,collectionId))){implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val childCollections = ListBuffer.empty[JsValue]
        val childCollectionIds = collection.child_collection_ids
        for (childCollectionId <- childCollectionIds) {
          collections.get(childCollectionId) match {
            case Some(child_collection) => {
              childCollections += jsonCollection(child_collection )
            }
            case None =>
              Logger.debug("No child collection with id : " + childCollectionId)
              collections.removeSubCollectionId(childCollectionId,collection)
          }
        }

        Ok(toJson(childCollections))
      }
      case None => BadRequest(toJson("collection not found"))
    }
  }

  def getParentCollections(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection,collectionId))){implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val parentCollections = ListBuffer.empty[JsValue]
        val parentCollectionIds = collection.parent_collection_ids
        for (parentCollectionId <- parentCollectionIds) {
          collections.get(parentCollectionId) match {
            case Some(parent_collection) => {
              parentCollections += jsonCollection(parent_collection )
            }
            case None =>
              Logger.debug("No parent collection with id : " + parentCollectionId)
              collections.removeParentCollectionId(parentCollectionId,collection)
          }
        }

        Ok(toJson(parentCollections))
      }

      case None => BadRequest(toJson("collection not found"))
    }
  }

  def removeFromSpaceAllowed(collectionId: UUID , spaceId : UUID) = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    val user = request.user
    user match {
      case Some(identity) => {
        val hasParentInSpace = collections.hasParentInSpace(collectionId, spaceId)
        Ok(toJson(!(hasParentInSpace)))
      }
      case None => Ok(toJson(false))
    }
  }

  def download(id: UUID, compression: Int) = PermissionAction(Permission.DownloadFiles, Some(ResourceRef(ResourceRef.collection, id))) { implicit request =>
    implicit val user = request.user
    collections.get(id) match {
      case Some(collection) => {
        val bagit = play.api.Play.configuration.getBoolean("downloadCollectionBagit").getOrElse(true)
        // Use custom enumerator to create the zip file on the fly
        // Use a 1MB in memory byte array
        Ok.chunked(enumeratorFromCollection(collection,1024*1024, compression,bagit,user)).withHeaders(
          "Content-Type" -> "application/zip",
          "Content-Disposition" -> ("attachment; filename=\"" + collection.name+ ".zip\"")
        )
      }
      // If the dataset wasn't found by ID
      case None => {
        NotFound
      }
    }
  }


  def enumeratorFromCollection(collection: Collection, chunkSize: Int = 1024 * 8, compression: Int = Deflater.DEFAULT_COMPRESSION, bagit: Boolean, user : Option[User])
                              (implicit ec: ExecutionContext): Enumerator[Array[Byte]] = {

    implicit val pec = ec.prepare()
    val md5Files = scala.collection.mutable.HashMap.empty[String, MessageDigest]
    val md5Bag = scala.collection.mutable.HashMap.empty[String, MessageDigest]

    var totalBytes = 0L

    val byteArrayOutputStream = new ByteArrayOutputStream(chunkSize)
    val zip = new ZipOutputStream(byteArrayOutputStream)

    var bytesSet = false

    //val datasetsInCollection = getDatasetsInCollection(collection,user.get)
    var current_iterator = new RootCollectionIterator(collection.name,collection,zip,md5Files,md5Bag,user, totalBytes,bagit,collections,
    datasets,files,folders,metadataService,spaces)



    //var current_iterator = new FileIterator(folderNameMap(inputFiles(1).id),inputFiles(1), zip,md5Files)
    var is = current_iterator.next()

    Enumerator.generateM({
      is match {
        case Some(inputStream) => {
          if (current_iterator.isBagIt() && bytesSet == false){
            current_iterator.setBytes(totalBytes)
            bytesSet = true
          }
          val buffer = new Array[Byte](chunkSize)
          val bytesRead = scala.concurrent.blocking {
            inputStream.read(buffer)

          }
          val chunk = bytesRead match {
            case -1 => {
              zip.closeEntry()
              inputStream.close()
              Some(byteArrayOutputStream.toByteArray)
              if (current_iterator.hasNext()){
                is = current_iterator.next()
              } else{
                zip.close()
                is = None
              }
              Some(byteArrayOutputStream.toByteArray)
            }
            case read => {
              if (!current_iterator.isBagIt()){
                totalBytes += bytesRead
              }
              zip.write(buffer, 0, read)
              Some(byteArrayOutputStream.toByteArray)
            }
          }
          byteArrayOutputStream.reset()
          Future.successful(chunk)
        }
        case None => {
          Future.successful(None)
        }
      }
    })(pec)

  }

}

