package api

import javax.inject.Inject

import com.wordnik.swagger.annotations.ApiOperation
import models.{ResourceRef, UUID}
import play.api.libs.json._
import services.UserService

/**
 * API to interact with the users.
 */
class Users @Inject()(users: UserService) extends ApiController {
  /**
   * Returns a list of all users in the system.
   */
  @ApiOperation(value = "List all users in the system",
    responseClass = "User", httpMethod = "GET")
  def list() = ServerAdminAction { implicit request =>
    Ok(Json.toJson(users.list))
  }

  /**
   * Returns the user that is making the request. Used to verify authentication, as well as for user data access.
   */
  @ApiOperation(value = "Return the user associated with the request.",
    responseClass = "User", httpMethod = "GET")
  def getUser = UserAction { implicit request =>
    request.mediciUser match {
      case Some(user) => Ok(Json.toJson(user))
      case None => Unauthorized("Not authenticated")
    }
  }

  /**
   * Returns a single user based on the id specified.
   */
  @ApiOperation(value = "Return a single user.",
    responseClass = "User", httpMethod = "GET")
  def findById(id: UUID) = PermissionAction(Permission.ViewUser, Some(ResourceRef(ResourceRef.user, id))) { implicit request =>
    users.findById(id) match {
      case Some(x) => Ok(Json.toJson(x))
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
      case Some(x) => Ok(Json.toJson(x))
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
  @ApiOperation(value = "Add a friend.",
    responseClass = "None", httpMethod = "POST")
  def addUserFriend(email: String, newFriend: String) = PermissionAction(Permission.ViewUser) { implicit request =>
    users.addUserFriend(email, newFriend)
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

}
