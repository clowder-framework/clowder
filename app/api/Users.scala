package api

import javax.inject.Inject
import com.wordnik.swagger.annotations.ApiOperation
import models.{UUID, User}
import play.api.libs.json._
import play.api.mvc.Action
import services.UserService
import java.util.Date
import models._
import services._
import play.api.Logger

/**
 * API to interact with the users.
 *
 * @author Rob Kooper
 */
class Users @Inject()(users: UserService, events: EventService) extends ApiController {
  /**
   * Returns a list of all users in the system.
   */
  @ApiOperation(value = "List all users in the system",
    responseClass = "User", httpMethod = "GET")
  def list() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.UserAdmin)) { request =>
    Ok(Json.toJson(users.list.map(userToJSON)))
  }

  /**
   * Returns the user that is making the request. Used to verify authentication, as well as for user data access.
   */
  @ApiOperation(value = "Return the user associated with the request.",
    responseClass = "User", httpMethod = "GET")
  def getUser() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
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
  def findById(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.findById(id) match {
      case Some(x) => Ok(userToJSON(x))
      case None => BadRequest("no user found with that id.")
    }
  }

  /**
   * Returns a single user based on the email specified.
   */
  @ApiOperation(value = "Return a single user.",
    responseClass = "User", httpMethod = "GET")
  def findByEmail(email: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.findByEmail(email) match {
      case Some(x) => Ok(userToJSON(x))
      case None => BadRequest("no user found with that email.")
    }
  }

  @ApiOperation(value = "Edit User Field.",
    responseClass = "None", httpMethod = "POST")
  def updateUserField(email: String, field: String, fieldText: Any) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.updateUserField(email, field, fieldText)
    Ok(Json.obj("status" -> "success"))
  }

  @ApiOperation(value = "Add a dataset View.",
    responseClass = "None", httpMethod = "POST")
  def addUserDatasetView(email: String, dataset: UUID)= SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.addUserDatasetView(email, dataset)
    Ok(Json.obj("status" -> "success"))
  }

  @ApiOperation(value = "Create a List.",
    responseClass = "None", httpMethod = "POST")
  def createNewListInUser(email: String, field: String, fieldList: List[Any])= SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.createNewListInUser(email, field, fieldList)
    Ok(Json.obj("status" -> "success"))
  }

  @ApiOperation(value = "Follow a user",
    responseClass = "None", httpMethod = "POST")
  def follow(followeeUUID: UUID, name: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.LoggedIn)) { request =>
    implicit val user = request.user
    user match {
      case Some(loggedInUser) => {
        val followerUUID = loggedInUser.id
        events.addObjectEvent(user, followeeUUID, name, "follow_user")
        users.followUser(followeeUUID, followerUUID)
        Ok(Json.obj("status" -> "success"))
      }
      case None => {
        Ok(Json.obj("status" -> "fail"))
      }
    }
  }

  @ApiOperation(value = "Unfollow a user",
    responseClass = "None", httpMethod = "POST")
  def unfollow(followeeUUID: UUID, name: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.LoggedIn)) { request =>
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

  def userToJSON(user: User): JsValue = {
    Json.obj("id" -> user.id.stringify,
      "firstName" -> user.firstName,
      "lastName" -> user.lastName,
      "fullName" -> user.fullName,
      "email" -> user.email,
      "avatar" -> user.avatarUrl)
  }
}
