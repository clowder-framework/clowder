package api

import java.util.Date
import javax.inject.Inject
import api.Permission.Permission
import com.wordnik.swagger.annotations.{ApiOperation, Api}
import models._
import play.api.Logger
import controllers.Utils
import play.api.Play._
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.libs.json.Json.toJson
import services.{EventService, AdminsNotifierPlugin, SpaceService, UserService, DatasetService, CollectionService}
import util.Mail
import scala.collection.mutable.ListBuffer
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 */
@Api(value = "/spaces", listingPath = "/api-docs.json/spaces", description = "Spaces are groupings of collections and datasets.")
class Spaces @Inject()(spaces: SpaceService, userService: UserService, datasetService: DatasetService,
  collectionService: CollectionService, events: EventService) extends ApiController {

  @ApiOperation(value = "Create a space",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  //TODO- Minimal Space created with Name and description. URLs are not yet put in
  def createSpace() = AuthenticatedAction(parse.json) { implicit request =>
    Logger.debug("Creating new space")
    val nameOpt = (request.body \ "name").asOpt[String]
    val descOpt = (request.body \ "description").asOpt[String]
    (nameOpt, descOpt) match {
      case (Some(name), Some(description)) => {
        // TODO: add creator
        val userId = request.user.get.id
        val c = ProjectSpace(name = name, description = description, created = new Date(), creator = userId,
          homePage = List.empty, logoURL = None, bannerURL = None, collectionCount = 0,
          datasetCount = 0, userCount = 0, metadata = List.empty)
        spaces.insert(c) match {
          case Some(id) => {
            Ok(toJson(Map("id" -> id)))
          }
          case None => Ok(toJson(Map("status" -> "error")))
        }

      }
      case (_, _) => BadRequest(toJson("Missing required parameters"))
    }
  }

  @ApiOperation(value = "Remove a space",
    notes = "Does not delete the individual datasets and collections in the space.",
    responseClass = "None", httpMethod = "DELETE")
  def removeSpace(spaceId: UUID) = PermissionAction(Permission.DeleteSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    spaces.get(spaceId) match {
      case Some(space) => {
        spaces.delete(spaceId)
        current.plugin[AdminsNotifierPlugin].foreach {
          _.sendAdminsNotification(Utils.baseUrl(request), "Space", "removed", space.id.stringify, space.name)
        }
      }
    }
    //Success anyway, as if space is not found it is most probably deleted already
    Ok(toJson(Map("status" -> "success")))
  }

  @ApiOperation(value = "Get a space",
    notes = "Retrieves information about a space",
    responseClass = "None", httpMethod = "GET")
  def get(id: UUID) = PermissionAction(Permission.ViewSpace, Some(ResourceRef(ResourceRef.space, id))) { implicit request =>
    spaces.get(id) match {
      case Some(space) => Ok(spaceToJson(Utils.decodeSpaceElements(space)))
      case None => BadRequest("Space not found")
    }
  }

  @ApiOperation(value = "List spaces the user can view",
    notes = "Retrieves information about spaces",
    responseClass = "None", httpMethod = "GET")
  def list(title: Option[String], date: Option[String], limit: Int) = UserAction { implicit request =>
    Ok(toJson(listSpaces(title, date, limit, Set[Permission](Permission.ViewSpace), false, request.user, request.superAdmin).map(spaceToJson)))
  }

  @ApiOperation(value = "List spaces the user can add to",
    notes = "Retrieves a list of spaces that the user has permission to add to",
    responseClass = "None", httpMethod = "GET")
  def listCanEdit(title: Option[String], date: Option[String], limit: Int) = UserAction { implicit request =>
    Ok(toJson(listSpaces(title, date, limit, Set[Permission](Permission.AddResourceToSpace, Permission.EditSpace), true, request.user, request.superAdmin).map(spaceToJson)))
  }

  /**
   * Returns list of collections based on parameters and permissions.
   * TODO this needs to be cleaned up when do permissions for adding to a resource
   */
  private def listSpaces(title: Option[String], date: Option[String], limit: Int, permission: Set[Permission], mine: Boolean, user: Option[User], superAdmin: Boolean) : List[ProjectSpace] = {
    if (mine && user.isEmpty) return List.empty[ProjectSpace]

    (title, date) match {
      case (Some(t), Some(d)) => {
        if (mine)
          spaces.listUser(d, true, limit, t, user, superAdmin, user.get)
        else
          spaces.listAccess(d, true, limit, t, permission, user, superAdmin)
      }
      case (Some(t), None) => {
        if (mine)
          spaces.listUser(limit, t, user, superAdmin, user.get)
        else
          spaces.listAccess(limit, t, permission, user, superAdmin)
      }
      case (None, Some(d)) => {
        if (mine)
          spaces.listUser(d, true, limit, user, superAdmin, user.get)
        else
          spaces.listAccess(d, true, limit, permission, user, superAdmin)
      }
      case (None, None) => {
        if (mine)
          spaces.listUser(limit, user, superAdmin, user.get)
        else
          spaces.listAccess(limit, permission, user, superAdmin)
      }
    }
  }

  def spaceToJson(space: ProjectSpace) = {
    toJson(Map("id" -> space.id.stringify,
      "name" -> space.name,
      "description" -> space.description,
      "created" -> space.created.toString))
  }

  @ApiOperation(value = " Associate a collection to a space",
  notes = "",
  responseClass = "None", httpMethod="POST"
  )
  def addCollectionToSpace(spaceId: UUID, collectionId: UUID) = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) {
    implicit request =>
      (spaces.get(spaceId), collectionService.get(collectionId)) match {
        case (Some(s), Some(c)) => {
          // TODO this needs to be cleaned up when do permissions for adding to a resource
          if (!Permission.checkOwner(request.user, ResourceRef(ResourceRef.dataset, collectionId))) {
            Forbidden(toJson(s"You are not the owner of the dataset"))
          } else {
            spaces.addCollection(collectionId, spaceId)
            Ok(Json.obj("collectionInSpace" -> (s.collectionCount + 1).toString))
          }
        }
        case (_, _) => NotFound
      }
  }

  @ApiOperation(value = " Associate a collection to a space",
    notes = "",
    responseClass = "None", httpMethod="POST"
  )
  def addDatasetToSpace(spaceId: UUID, datasetId: UUID) = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) {
    implicit request =>
      (spaces.get(spaceId), datasetService.get(datasetId)) match {
        case (Some(s), Some(d)) => {
          // TODO this needs to be cleaned up when do permissions for adding to a resource
          if (!Permission.checkOwner(request.user, ResourceRef(ResourceRef.dataset, datasetId))) {
            Forbidden(toJson(s"You are not the owner of the dataset"))
          } else {
            spaces.addDataset(datasetId, spaceId)
            Ok(Json.obj("datasetsInSpace" -> (s.datasetCount + 1).toString))
          }
        }
        case (_, _) => NotFound
      }
  }

  @ApiOperation(value = "Remove a collection from a space",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def removeCollection(spaceId: UUID, collectionId: UUID) = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    (spaces.get(spaceId), collectionService.get(collectionId)) match {
      case (Some(s), Some(c)) => {
        spaces.removeCollection(collectionId, spaceId)
        Ok(toJson("success"))
      }
      case (_, _) => NotFound
    }
  }

  @ApiOperation(value = "Remove a dataset from a space",
  notes = "",
  responseClass = "None", httpMethod = "POST")
  def removeDataset(spaceId: UUID, datasetId: UUID) = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    (spaces.get(spaceId), datasetService.get(datasetId)) match {
      case (Some(s), Some(d)) => {
        spaces.removeDataset(datasetId, spaceId)
        Ok(toJson("success"))
      }
      case (_, _) => NotFound
    }
  }

  /**
   * REST endpoint: POST call to update the configuration information associated with a specific Space
   *
   * Takes one arg, id:
   *
   * id, the UUID associated with the space that will be updated
   *
   * The data contained in the request body will defined by the following String key-value pairs:
   *
   * description -> The text for the updated description for the space
   * name -> The text for the updated name for this space
   * timetolive -> Text that represents an integer for the number of hours to retain resources
   * enabled -> Text that represents a boolean flag for whether or not the space should purge resources that have expired
   *
   */
  @ApiOperation(value = "Update the information associated with a space", notes = "",
    responseClass = "None", httpMethod = "POST")
  def updateSpace(spaceid: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, spaceid)))(parse.json) { implicit request =>
    if (UUID.isValid(spaceid.stringify)) {

      //Set up the vars we are looking for
      var description: String = null
      var name: String = null
      var timeAsString: String = null
      var enabled: Boolean = false

      var aResult: JsResult[String] = (request.body \ "description").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          description = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson("description data is missing from the updateSpace call."))
        }
      }

      aResult = (request.body \ "name").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          name = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson("name data is missing from the updateSpace call."))
        }
      }

      aResult = (request.body \ "timetolive").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          timeAsString = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson("timetolive data is missing from the updateSpace call."))
        }
      }

      // Pattern matching
      (request.body \ "enabled").validate[Boolean] match {
        case b: JsSuccess[Boolean] => {
          enabled = b.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson("enabled data is missing from the updateSpace call."))
        }
      }

      Logger.debug(s"updateInformation for dataset with id  $spaceid. Args are $description, $name, $enabled, and $timeAsString")

      //Generate the expiration time and the boolean flag
      val timeToLive = timeAsString.toInt * 60 * 60 * 1000L
      //val expireEnabled = enabledAsString.toBoolean
      Logger.debug("converted values are " + timeToLive + " and " + enabled)

      spaces.updateSpaceConfiguration(spaceid, name, description, timeToLive, enabled)
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $spaceid is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $spaceid is not a valid ObjectId."))
    }
  }

  /**
   * REST endpoint: POST call to update the user information associated with a specific Space
   *
   * Takes one arg, spaceId:
   *
   * spaceId, the UUID associated with the space that will be updated
   *
   * The data contained in the request body will defined by the following String key-value pairs:
   *
   * rolesandusers -> A map that contains a role level as a key and a comma separated String of user IDs as the value
   *
   */
  @ApiOperation(value = "Update the information associated with a space", notes = "",
    responseClass = "None", httpMethod = "POST")
  def updateUsers(spaceId: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, spaceId)))(parse.json) { implicit request =>
    val user = request.user
    if (UUID.isValid(spaceId.stringify)) {
      val aResult: JsResult[Map[String, String]] = (request.body \ "rolesandusers").validate[Map[String, String]]

      // Pattern matching
      aResult match {
        case aMap: JsSuccess[Map[String, String]] => {
          //Set up a map of existing users to check against
          val existingUsers = spaces.getUsersInSpace(spaceId)
          var existUserRole: Map[String, String] = Map.empty
          for (aUser <- existingUsers) {
            spaces.getRoleForUserInSpace(spaceId, aUser.id) match {
              case Some(aRole) => {
                existUserRole += (aUser.id.stringify -> aRole.name)
              }
              case None => Logger.debug("This shouldn't happen. A user in a space should always have a role.")
            }
          }

          val roleMap: Map[String, String] = aMap.get

          for ((k, v) <- roleMap) {
            //Deal with users that were removed
            userService.findRoleByName(k) match {
              case Some(aRole) => {
                val idArray: Array[String] = v.split(",").map(_.trim())
                for (existUserId <- existUserRole.keySet) {
                  if (!idArray.contains(existUserId)) {
                    //Check if the role is for this level
                    existUserRole.get(existUserId) match {
                      case Some(existRole) => {
                        if (existRole == k) {
                          //In this case, the level is correct, so it is a removal
                          spaces.removeUser(UUID(existUserId), spaceId)
                        }
                      }
                      case None => Logger.debug("This should never happen. A user in a space should always have a role.")
                    }
                  }
                }
              }
              case None => Logger.debug("A role was sent up that doesn't exist. It is " + k)
            }
          }
          spaces.get(spaceId) match {
            case Some(space) => {
              for ((k, v) <- roleMap) {
                //The role needs to exist
                userService.findRoleByName(k) match {
                  case Some(aRole) => {
                    val idArray: Array[String] = v.split(",").map(_.trim())

                    //Deal with all the ids that were sent up (changes and adds)
                    for (aUserId <- idArray) {
                      //For some reason, an empty string is getting through as aUserId on length
                      if (aUserId != "") {
                        if (existUserRole.contains(aUserId)) {
                          //The user exists in the space already
                          existUserRole.get(aUserId) match {
                            case Some(existRole) => {
                              if (existRole != k) {
                                spaces.changeUserRole(UUID(aUserId), aRole, spaceId)
                              }
                            }
                            case None => Logger.debug("This shouldn't happen. A user that is assigned to a space should always have a role.")
                          }
                        }
                        else {
                          //New user completely to the space
                          spaces.addUser(UUID(aUserId), aRole, spaceId)
                          events.addRequestEvent(user, userService.get(UUID(aUserId)).get, spaceId, spaces.get(spaceId).get.name, "add_user_to_space")
                          val newmember = userService.get(UUID(aUserId))
                          val theHtml = views.html.spaces.inviteNotificationEmail(spaceId.stringify, space.name, user.get.getMiniUser, newmember.get.getMiniUser.fullName, aRole.name)
                          Mail.sendEmail("Added to Space", newmember.get.getMiniUser.email.get ,theHtml)
                        }
                      }
                      else {
                        Logger.debug("There was an empty string that counted as an array...")
                      }
                    }
                  }
                  case None => Logger.debug("A role was sent up that doesn't exist. It is " + k)
                }
              }
              if(space.userCount != spaces.getUsersInSpace(space.id).length){
                spaces.updateUserCount(space.id, spaces.getUsersInSpace(space.id).length)
              }
            }
            case None => Logger.error("Errors: " + "Could not find space")
          }



          Ok(Json.obj("status" -> "success"))
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson("rolesandusers data is missing from the updateUsers call."))
        }
      }
    }
    else {
      Logger.error(s"The given id $spaceId is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $spaceId is not a valid ObjectId."))
    }
  }


  @ApiOperation(value = "Remove a user from a space", notes = "",
    responseClass = "None", httpMethod = "GET")
  def removeUser(spaceId: UUID, removeUser:String) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    val user = request.user
    if(spaces.getRoleForUserInSpace(spaceId, UUID(removeUser)) != None){
      spaces.removeUser(UUID(removeUser), spaceId)
      events.addRequestEvent(user, userService.get(UUID(removeUser)).get, spaceId, spaces.get(spaceId).get.name, "remove_user_from_space")
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(s"Remove User $removeUser from space $spaceId does not exist.")
      BadRequest(toJson(s"The given id $spaceId is not a valid ObjectId."))
    }

  }


  @ApiOperation(value = "Follow space",
    notes = "Add user to space followers and add space to user followed spaces.",
    responseClass = "None", httpMethod = "POST")
  def follow(id: UUID, name: String) = AuthenticatedAction { implicit request =>
    implicit val user = request.user

    user match {
      case Some(loggedInUser) => {
        spaces.get(id) match {
          case Some(file) => {
            events.addObjectEvent(user, id, name, "follow_space")
            spaces.addFollower(id, loggedInUser.id)
            userService.followResource(loggedInUser.id, new ResourceRef(ResourceRef.space, id))

            val recommendations = getTopRecommendations(id, loggedInUser)
            recommendations match {
              case x :: xs => Ok(Json.obj("status" -> "success", "recommendations" -> recommendations))
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



  @ApiOperation(value = "Unfollow space",
    notes = "Remove user from space followers and remove space from user followed spaces.",
    responseClass = "None", httpMethod = "POST")
  def unfollow(id: UUID, name: String) = AuthenticatedAction { implicit request =>
    implicit val user = request.user

    user match {
      case Some(loggedInUser) => {
        spaces.get(id) match {
          case Some(file) => {
            events.addObjectEvent(user, id, name, "unfollow_space")
            spaces.removeFollower(id, loggedInUser.id)
            userService.unfollowResource(loggedInUser.id, new ResourceRef(ResourceRef.space, id))
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
    val followeeModel = spaces.get(followeeUUID)
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
}
