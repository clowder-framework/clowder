package api

import javax.inject.Inject
import com.wordnik.swagger.annotations.ApiOperation
import play.api.libs.json._
import play.api.Play.current
import services.UserService
import models._
import services._
import play.api.Logger

/**
 * API to interact with the users.
 */
class Users @Inject()(users: UserService, events: EventService) extends ApiController {
  /**
   * Returns a list of all users in the system.
   */
  @ApiOperation(value = "List all users in the system",
    responseClass = "User", httpMethod = "GET")
  def list() = ServerAdminAction { implicit request =>
     Ok(Json.toJson(users.list.map(userToJSON)))
  }

  /**
   * Returns the user that is making the request. Used to verify authentication, as well as for user data access.
   */
  @ApiOperation(value = "Return the user associated with the request.",
    responseClass = "User", httpMethod = "GET")
  def getUser() = AuthenticatedAction { implicit request =>
      request.user match {
          case Some(identity) => {
              Logger.debug("Have an identity. It is " + identity)
              identity.email match {
                  case Some(emailAddress) => {
		              users.findByEmail(emailAddress) match {
		                  case Some(user) => Ok(userToJSON(user))
		                  //The None case should never happen, as this is a secured action, and requires a user?
		                  case None => {
		                      Logger.debug("--------- In the NONE case for findById in getUser")
		                      Ok(Json.toJson("No user found"))
		                  }
		              }
                  }
                  case None => Unauthorized("Not authenticated")
              }
          }
          case None => {
              Unauthorized("Not authenticated")
          }
      }
  }  
  
  /**
   * Returns a single user based on the id specified.
   */
  @ApiOperation(value = "Return a single user.",
    responseClass = "User", httpMethod = "GET")
  def findById(id: UUID) = PermissionAction(Permission.ViewUser, Some(ResourceRef(ResourceRef.user, id))) { implicit request =>
    users.findById(id) match {
      case Some(x) => Ok(userToJSON(x))
      case None => BadRequest("no user found with that id.")
    }
  }

  /**
   * Returns a single user based on the email specified.
   * @deprecated use findById
   */
  @ApiOperation(value = "Return a single user.",
    responseClass = "User", httpMethod = "GET")
  def findByEmail(email: String) = PermissionAction(Permission.ViewUser) { implicit request =>
    users.findByEmail(email) match {
      case Some(x) => Ok(userToJSON(x))
      case None => BadRequest("no user found with that email.")
    }
  }

  /** @deprecated use id instead of email */
  @ApiOperation(value = "Edit User Field.",
    responseClass = "None", httpMethod = "POST")
  def updateUserField(email: String, field: String, fieldText: Any) = PermissionAction(Permission.ViewUser) { implicit request =>
    users.updateUserField(email, field, fieldText)
    Ok(Json.obj("status" -> "success"))
  }

  /** @deprecated use id instead of email */
  @ApiOperation(value = "Add a dataset View.",
    responseClass = "None", httpMethod = "POST")
  def addUserDatasetView(email: String, dataset: UUID) = PermissionAction(Permission.ViewUser) { implicit request =>
    users.addUserDatasetView(email, dataset)
    Ok(Json.obj("status" -> "success"))
  }

  /** @deprecated use id instead of email */
  @ApiOperation(value = "Create a List.",
    responseClass = "None", httpMethod = "POST")
  def createNewListInUser(email: String, field: String, fieldList: List[Any])= PermissionAction(Permission.ViewUser) { implicit request =>
    users.createNewListInUser(email, field, fieldList)
    Ok(Json.obj("status" -> "success"))
  }

  @ApiOperation(value = "Follow a user",
    responseClass = "None", httpMethod = "POST")
  def follow(followeeUUID: UUID, name: String) = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(loggedInUser) => {
        val followerUUID = loggedInUser.id
        events.addObjectEvent(user, followeeUUID, name, "follow_user")
        users.followUser(followeeUUID, followerUUID)

        val recommendations = getTopRecommendations(followeeUUID, loggedInUser)
        recommendations match {
          case x::xs => Ok(Json.obj("status" -> "success", "recommendations" -> recommendations))
          case Nil => Ok(Json.obj("status" -> "success"))
        }
      }
      case None => {
        Ok(Json.obj("status" -> "fail"))
      }
    }
  }

  @ApiOperation(value = "Unfollow a user",
    responseClass = "None", httpMethod = "POST")
  def unfollow(followeeUUID: UUID, name: String) = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(loggedInUser) => {
        val followerUUID = loggedInUser.id
        events.addObjectEvent(user, followeeUUID, name, "unfollow_user")
        users.unfollowUser(followeeUUID, followerUUID)
        Ok(Json.obj("status" -> "success"))
      }
      case None => {
        Ok(Json.obj("status" -> "fail"))
      }
    }
  }

  def getTopRecommendations(followeeUUID: UUID, follower: User): List[MiniEntity] = {
    val followeeModel = users.findById(followeeUUID)
    followeeModel match {
      case Some(followeeModel) => {
        val sourceFollowerIDs = followeeModel.followers
        val excludeIDs = follower.followedEntities.map(typedId => typedId.id) ::: List(followeeUUID, follower.id)
        val num = play.api.Play.configuration.getInt("number_of_recommendations").getOrElse(10)
        users.getTopRecommendations(sourceFollowerIDs, excludeIDs, num)
      }
      case None => {
        List.empty
      }
    }
  }

  def userToJSON(user: User): JsValue = {
    var profile: Map[String, JsValue] = Map.empty
    if(user.profile.isDefined) {
      if(user.profile.get.biography.isDefined) {
        profile  = profile + ("biography" -> Json.toJson(user.profile.get.biography))
      }
      if(user.profile.get.institution.isDefined) {
        profile = profile + ( "institution" -> Json.toJson(user.profile.get.institution))
      }
      if(user.profile.get.orcidID.isDefined) {
        profile = profile + ("orcidId" -> Json.toJson(user.profile.get.orcidID))
      }
      if(user.profile.get.position.isDefined) {
        profile = profile + ("position" -> Json.toJson(user.profile.get.position))
      }
      if(user.profile.get.institution.isDefined) {
        profile = profile + ("institution" -> Json.toJson(user.profile.get.institution))
      }

    }
    Json.obj("id" -> user.id.stringify,
      "firstName" -> user.firstName,
      "lastName" -> user.lastName,
      "fullName" -> user.fullName,
      "email" -> user.email,
      "avatar" -> user.getAvatarUrl(),
      "profile" -> Json.toJson(profile)
     )
  }
}
