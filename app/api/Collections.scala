package api

import api.Permission.Permission
import play.api.Logger
import play.api.Play.current
import models._
import services._
import play.api.libs.json._
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json.toJson
import javax.inject.{ Singleton, Inject}
import scala.collection.mutable.ListBuffer
import scala.util.parsing.json.JSONArray
import scala.util.{Try, Success, Failure}
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import java.util.Date
import controllers.Utils


/**
 * Manipulate collections.
 */
@Api(value = "/collections", listingPath = "/api-docs.json/collections", description = "Collections are groupings of datasets")
@Singleton
class Collections @Inject() (datasets: DatasetService, collections: CollectionService, previews: PreviewService, userService: UserService, events: EventService, spaces:SpaceService) extends ApiController {

  @ApiOperation(value = "Create a collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
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
              c.spaces.map{ s =>
                spaces.addCollection(c.id, s)
                collections.addToRootSpaces(c.id, s)

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


  @ApiOperation(value = "Add dataset to collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def attachDataset(collectionId: UUID, datasetId: UUID) = PermissionAction(Permission.AddResourceToCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    // TODO this needs to be cleaned up when do permissions for adding to a resource
    if (!Permission.checkOwner(request.user, ResourceRef(ResourceRef.dataset, datasetId))) {
      Forbidden(toJson(s"You are not the owner of the dataset"))
    } else {
      collections.addDataset(collectionId, datasetId) match {
        case Success(_) => {
          var datasetsInCollection = 0
          collections.get(collectionId) match {
            case Some(collection) => {
              datasets.get(datasetId) match {
                case Some(dataset) => {
                  events.addSourceEvent(request.user , dataset.id, dataset.name, collection.id, collection.name, "attach_dataset_collection")
                }
              }
              datasetsInCollection = collection.datasetCount
            }
          }
          //datasetsInCollection is the number of datasets in this collection
          Ok(Json.obj("datasetsInCollection" -> Json.toJson(datasetsInCollection) ))
        }
        case Failure(t) => InternalServerError
      }
    }
  }

  /**
   * Reindex the given collection, if recursive is set to true it will
   * also reindex all datasets and files.
   */
  @ApiOperation(value = "Reindex a collection",
    notes = "Reindex the existing collection, if recursive is set to true it will also reindex all datasets and files.",
    httpMethod = "GET")
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

  @ApiOperation(value = "Remove dataset from collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def removeDataset(collectionId: UUID, datasetId: UUID, ignoreNotFound: String) = PermissionAction(Permission.EditCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    collections.removeDataset(collectionId, datasetId, Try(ignoreNotFound.toBoolean).getOrElse(true)) match {
      case Success(_) => {

        collections.get(collectionId) match {
        case Some(collection) => {
          datasets.get(datasetId) match {
            case Some(dataset) => {
              events.addSourceEvent(request.user , dataset.id, dataset.name, collection.id, collection.name, "remove_dataset_collection")
            }
          }
        }
      }
      Ok(toJson(Map("status" -> "success")))
    }
    case Failure(t) => {
      Logger.error("Error: " + t)
      InternalServerError
    }
    }
  }

  @ApiOperation(value = "Remove collection",
      notes = "Does not delete the individual datasets in the collection.",
      responseClass = "None", httpMethod = "DELETE")
  def removeCollection(collectionId: UUID) = PermissionAction(Permission.DeleteCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        events.addObjectEvent(request.user , collection.id, collection.name, "delete_collection")
        collections.delete(collectionId)
        current.plugin[AdminsNotifierPlugin].foreach {
          _.sendAdminsNotification(Utils.baseUrl(request),"Collection","removed",collection.id.stringify, collection.name)
        }
      }
    }
    //Success anyway, as if collection is not found it is most probably deleted already
    Ok(toJson(Map("status" -> "success")))
  }

  @ApiOperation(value = "List all collections the user can view",
    notes = "This will check for Permission.ViewCollection",
    responseClass = "None", multiValueResponse=true, httpMethod = "GET")
  def list(title: Option[String], date: Option[String], limit: Int) = PrivateServerAction { implicit request =>
    Ok(toJson(lisCollections(title, date, limit, Set[Permission](Permission.ViewCollection), false, request.user, request.superAdmin)))
  }

  @ApiOperation(value = "List all collections the user can edit",
    notes = "This will check for Permission.AddResourceToCollection and Permission.EditCollection",
    responseClass = "None", httpMethod = "GET")
  def listCanEdit(title: Option[String], date: Option[String], limit: Int) = PrivateServerAction { implicit request =>
    Ok(toJson(lisCollections(title, date, limit, Set[Permission](Permission.AddResourceToCollection, Permission.EditCollection), false, request.user, request.superAdmin)))
  }


  @ApiOperation(value = "List all collections the user can edit except itself and its parent collections",
    notes = "This will check for Permission.AddResourceToCollection and Permission.EditCollection",
    responseClass = "None", httpMethod = "GET")
  def listPossibleParents(currentCollectionId : String, title: Option[String], date: Option[String], limit: Int) = PrivateServerAction { implicit request =>
    val selfAndAncestors = collections.getSelfAndAncestors(UUID(currentCollectionId))
    val descendants = collections.getAllDescendants(UUID(currentCollectionId)).toList
    val allCollections = lisCollections(None, None, limit, Set[Permission](Permission.AddResourceToCollection, Permission.EditCollection), false, request.user, request.superAdmin)
    val possibleNewParents = allCollections.filter((c: Collection) => (!selfAndAncestors.contains(c) && !descendants.contains(c)))
    Ok(toJson(possibleNewParents))
  }



  /**
   * Returns list of collections based on parameters and permissions.
   * TODO this needs to be cleaned up when do permissions for adding to a resource
   */
  private def lisCollections(title: Option[String], date: Option[String], limit: Int, permission: Set[Permission], mine: Boolean, user: Option[User], superAdmin: Boolean) : List[Collection] = {
    if (mine && user.isEmpty) return List.empty[Collection]

    (title, date) match {
      case (Some(t), Some(d)) => {
        if (mine)
          collections.listUser(d, true, limit, t, user, superAdmin, user.get)
        else
          collections.listAccess(d, true, limit, t, permission, user, superAdmin)
      }
      case (Some(t), None) => {
        if (mine)
          collections.listUser(limit, t, user, superAdmin, user.get)
        else
          collections.listAccess(limit, t, permission, user, superAdmin)
      }
      case (None, Some(d)) => {
        if (mine)
          collections.listUser(d, true, limit, user, superAdmin, user.get)
        else
          collections.listAccess(d, true, limit, permission, user, superAdmin)
      }
      case (None, None) => {
        if (mine)
          collections.listUser(limit, user, superAdmin, user.get)
        else
          collections.listAccess(limit, permission, user, superAdmin)
      }
    }
  }

  @ApiOperation(value = "Get a specific collection",
    responseClass = "Collection", httpMethod = "GET")
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

  @ApiOperation(value = "Update a collection name",
  notes= "Takes one argument, a UUID of the collection. Request body takes a key-value pair for the name",
  responseClass = "None", httpMethod = "PUT")
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
        Logger.debug(s"Update title for dataset with id $id. New name: $name")
        collections.updateName(id, name)
        collections.get(id) match {
          case Some(collection) => {
            events.addObjectEvent(user, id, collection.name, "update_collection_information")
          }

        }
        Ok(Json.obj("status" -> "success"))
      }
      else {
        Logger.error(s"The given id $id is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }
  }

  @ApiOperation(value = "Update collection description",
  notes = "Takes one argument, a UUID of the collection. Request body takes key-value pair for the description",
  responseClass = "None", httpMethod = "PUT")
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
        Logger.debug(s"Update title for dataset with id $id. New description: $description")
        collections.updateDescription(id, description)
        collections.get(id) match {
          case Some(collection) => {
            events.addObjectEvent(user, id, collection.name, "update_collection_information")
          }

        }
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
  @ApiOperation(value = "Attach existing preview to collection",
    notes = "",
    responseClass = "None", httpMethod = "POST")
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

  @ApiOperation(value = "Follow collection.",
    notes = "Add user to collection followers and add collection to user followed collections.",
    responseClass = "None", httpMethod = "POST")
  def follow(id: UUID, name: String) = AuthenticatedAction { implicit request =>
      implicit val user = request.user

      user match {
        case Some(loggedInUser) => {
          collections.get(id) match {
            case Some(collection) => {
              events.addObjectEvent(user, id, name, "follow_collection")
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

  @ApiOperation(value = "Unfollow collection.",
    notes = "Remove user from collection followers and remove collection from user followed collections.",
    responseClass = "None", httpMethod = "POST")
  def unfollow(id: UUID, name: String) = AuthenticatedAction { implicit request =>
      implicit val user = request.user

      user match {
        case Some(loggedInUser) => {
          collections.get(id) match {
            case Some(collection) => {
              events.addObjectEvent(user, id, name, "unfollow_collection")
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


  @ApiOperation(value = "Add subcollection to collection",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def attachSubCollection(collectionId: UUID, subCollectionId: UUID) = PermissionAction(Permission.AddResourceToCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    collections.addSubCollection(collectionId, subCollectionId) match {
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



  @ApiOperation(value = "Create a collection with parent",
    notes = "",
    responseClass = "None", httpMethod = "POST")
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
              c.spaces.map{ s =>
                spaces.addCollection(c.id, s)
                collections.addToRootSpaces(c.id, s)
              }

              //do stuff with parent here
              (request.body \"parentId").asOpt[String] match {
                case Some(parentId) => {
                  collections.get(UUID(parentId)) match {
                    case Some(parentCollection) => {
                      collections.addSubCollection(UUID(parentId), UUID(id)) match {
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

  @ApiOperation(value = "Remove subcollection from collection",
    notes="",
    responseClass = "None", httpMethod = "POST")
  def removeSubCollection(collectionId: UUID, subCollectionId: UUID, ignoreNotFound: String) = PermissionAction(Permission.AddResourceToCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>

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
  @ApiOperation(value = "Add root flags for a collection in space",
    notes = "",
    responseClass = "None",httpMethod = "POST")
  def setRootSpace(collectionId: UUID, spaceId: UUID)  = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    Logger.debug("changing the value of the root flag")
    collections.get(collectionId) match {
      case Some(collection) => {
        spaces.addCollection(collectionId, spaceId)
        collections.addToRootSpaces(collectionId, spaceId)
        Ok(jsonCollection(collection))
      } case None => {
        Logger.error("Error getting collection  " + collectionId)
        BadRequest(toJson(s"The given collection id $collectionId is not a valid ObjectId."))
      }
    }
  }

  /**
    * Remove root flag from a collection in a space
    */
  @ApiOperation(value = "Removes root flag from a collection in  a space",
    notes = "",
    responseClass = "None",httpMethod = "POST")
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

  @ApiOperation(value = "Get all root collections",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getRootCollections() = PermissionAction(Permission.ViewCollection) { implicit request =>
    val root_collections_list = for (collection <- collections.listAccess(100,Set[Permission](Permission.ViewCollection),request.user,true); if collections.hasRoot(collection)  )
      yield jsonCollection(collection)

    Ok(toJson(root_collections_list))
  }

  @ApiOperation(value = "Get all collections",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getAllCollections() = PermissionAction(Permission.ViewCollection) { implicit request =>
    implicit val user = request.user
    val count : Long  = collections.countAccess(Set[Permission](Permission.ViewCollection),user,true)
    val limit = count.toInt
    val all_collections_list = for (collection <- collections.listAccess(limit,Set[Permission](Permission.ViewCollection),request.user,true))
      yield jsonCollection(collection)
    Ok(toJson(all_collections_list))
  }



  @ApiOperation(value = "Get all root collections or collections that do not have a parent",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getTopLevelCollections() = PermissionAction(Permission.ViewCollection){ implicit request =>
    implicit val user = request.user
    val count = collections.countAccess(Set[Permission](Permission.ViewCollection),user,true)
    val limit = count.toInt
    val top_level_collections = for (collection <- collections.listAccess(limit,Set[Permission](Permission.ViewCollection),request.user,true); if (collections.hasRoot(collection) || collection.parent_collection_ids.isEmpty))
      yield jsonCollection(collection)
    Ok(toJson(top_level_collections))
  }

  @ApiOperation(value = "Get child collection ids in collection",
    responseClass = "None", httpMethod = "GET")
  def getChildCollectionIds(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection,collectionId))){implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val childCollectionIds = collection.child_collection_ids
        Ok(toJson(childCollectionIds))
      }
      case None => BadRequest(toJson("collection not found"))
    }
  }

  @ApiOperation(value = "Get parent collection ids in collection",
    responseClass = "None", httpMethod = "GET")
  def getParentCollectionIds(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection,collectionId))){implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val parentCollectionIds = collection.parent_collection_ids
        Ok(toJson(parentCollectionIds))
      }
      case None => BadRequest(toJson("collection not found"))
    }
  }



  @ApiOperation(value = "Get child collections in collection",
    responseClass = "None", httpMethod = "GET")
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

  @ApiOperation(value = "Get parent collections for collection",
    responseClass = "None", httpMethod = "GET")
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

  @ApiOperation(value = "Checks if we can remove a collection from a space",
    responseClass = "None", httpMethod = "GET")
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

}

