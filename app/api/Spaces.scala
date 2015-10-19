package api

import java.util.Date
import javax.inject.Inject
import com.wordnik.swagger.annotations.{ApiOperation, Api}
import models._
import play.api.Logger
import controllers.Utils
import play.api.Play._
import play.api.libs.json.Json
import play.api.libs.json.Json._
import services.{EventService, AdminsNotifierPlugin, SpaceService, UserService, DatasetService, CollectionService}
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import scala.collection.mutable.ListBuffer

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 */
@Api(value = "/spaces", listingPath = "/api-docs.json/spaces", description = "Spaces are groupings of collections and datasets.")
class Spaces @Inject()(spaces: SpaceService, userService: UserService, datasetService: DatasetService, collectionService: CollectionService, events: EventService) extends ApiController {

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

  @ApiOperation(value = "List spaces",
    notes = "Retrieves information about spaces",
    responseClass = "None", httpMethod = "GET")
  def list() = UserAction { implicit request =>
    var decodedSpaceList = new ListBuffer[models.ProjectSpace]()

    val userSpaces = request.user match {
      case Some(user) => user.spaceandrole.map(_.spaceId).flatMap(spaces.get(_))
      case None => spaces.list()
    }

    for (aSpace <- userSpaces) {
      decodedSpaceList += Utils.decodeSpaceElements(aSpace)
    }
    Ok(toJson(decodedSpaceList.toList.map(spaceToJson)))
  }

  @ApiOperation(value = "List spaces a user can add to",
    notes = "Retrieves a list of spaces that the user has permission to add to",
    responseClass = "None", httpMethod = "GET")
  def listSpacesCanAdd(title: Option[String], date: Option[String], limit: Int) = UserAction { implicit request =>
    val list = (title, date) match {
      case (Some(t), Some(d)) => {
        spaces.listAccess(d, true, limit, t, request.user, request.superAdmin)
      }
      case (Some(t), None) => {
        spaces.listAccess(limit, t, request.user, request.superAdmin)
      }
      case (None, Some(d)) => {
        spaces.listAccess(d, true, limit, request.user, request.superAdmin)
      }
      case (None, None) => {
        spaces.listAccess(limit, request.user, request.superAdmin)
      }
    }

    Logger.debug(list.map(s =>s.name+"  ").toString())

    var decodedSpaceList = new ListBuffer[models.ProjectSpace]()

    val userSpaces = request.user match {
      case Some(user) => list.filter(s => user.spaceandrole.map(_.spaceId).contains(s.id))
      case None => List.empty
    }

    implicit val user = request.user
    for (aSpace <- userSpaces) {
      //For each space in the list, check if the user has permission to add something to it, if so
      //decode it and add it to the list to pass back to the view.
      if (Permission.checkPermission(Permission.AddResourceToSpace, ResourceRef(ResourceRef.space, aSpace.id))) {
        decodedSpaceList += Utils.decodeSpaceElements(aSpace)
      }
    }

    Ok(toJson(decodedSpaceList.toList.map(spaceToJson)))
  }

  def spaceToJson(space: ProjectSpace) = {
    toJson(Map("id" -> space.id.stringify,
      "name" -> space.name,
      "description" -> space.description,
      "created" -> space.created.toString))
  }

  @ApiOperation(value = "Associate a collection with a space",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def addCollection(space: UUID) = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, space)))(parse.json) { implicit request =>
    val collectionId = (request.body \ "collection_id").as[String]
    spaces.addCollection(UUID(collectionId), space)
    Ok(toJson("success"))
  }

  @ApiOperation(value = " Associate a collection to multiple spaces",
  notes = "",
  responseClass = "None", httpMethod="POST"
  )
  def addCollectionToSpaces(space_list: List[String], collection_id: UUID) = PermissionAction(Permission.EditCollection)(parse.json) {
    implicit request =>
      val current_spaces = collectionService.get(collection_id).map(_.spaces).get
      var new_spaces: List[UUID] = List.empty
      current_spaces.map{
        aSpace =>
          if(!space_list.contains(aSpace.toString)) {
            spaces.removeCollection(collection_id, aSpace)
          }
          else {
            new_spaces = aSpace :: new_spaces
          }
      }
      space_list.map {
        aSpace => if(!new_spaces.contains(aSpace) && !current_spaces.contains(UUID(aSpace))) {
          new_spaces = UUID(aSpace) :: new_spaces
        }
      }
      new_spaces.map(space_id => spaces.addCollection(collection_id, space_id))
      Ok(toJson("success"))
  }

  @ApiOperation(value = "Associate a dataset with a space",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def addDataset(space: UUID) = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, space)))(parse.json) { implicit request =>
    val datasetId = (request.body \ "dataset_id").as[String]
    spaces.addDataset(UUID(datasetId), space)
    Ok(toJson("success"))
  }

  @ApiOperation(value = "Associate a dataset to multiple spaces",
  notes= "",
  responseClass="None", httpMethod ="POST")
  def addDatasetToSpaces(space_list: List[String], dataset_id: UUID) = PermissionAction(Permission.EditCollection)(parse.json) {
    implicit request =>
      val current_spaces = datasetService.get(dataset_id).map(_.spaces).get
      var new_spaces: List[UUID] = List.empty
      current_spaces.map{
        aSpace =>
          if(!space_list.contains(aSpace.toString)) {
            spaces.removeDataset(dataset_id, aSpace)
          }
          else {
            new_spaces = aSpace :: new_spaces
          }
      }
      space_list.map {
        aSpace => if(!new_spaces.contains(aSpace) && !current_spaces.contains(UUID(aSpace))) {
          new_spaces = UUID(aSpace) :: new_spaces
        }
      }
      new_spaces.map(space_id => spaces.addDataset(dataset_id, space_id))
      Ok(toJson("success"))
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
          val space = spaces.get(spaceId)
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
                      val newmember = userService.get(UUID(aUserId))
                      val theHtml = views.html.spaces.inviteNotificationEmail(spaceId.stringify, space.get.name, user.get.getMiniUser, newmember.get.getMiniUser.fullName, aRole.name)
                      controllers.Users.sendEmail("Added to Space", newmember.get.getMiniUser.email.get ,theHtml)
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
