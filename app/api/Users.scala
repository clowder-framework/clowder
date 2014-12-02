package api

import javax.inject.Inject

import com.wordnik.swagger.annotations.ApiOperation
import models.{UUID, User}
import play.api.libs.json._
import play.api.mvc.Action
import services.UserService

/**
 * API to interact with the users.
 *
 * @author Rob Kooper
 */
class Users @Inject()(users: UserService) extends ApiController {
  /**
   * Returns a list of all users in the system.
   */
  @ApiOperation(value = "List all users in the system",
    responseClass = "User", httpMethod = "GET")
  def list() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.UserAdmin)) { request =>
    Ok(Json.toJson(users.list))
  }

  /**
   * Returns a single user based on the id specified.
   */
  @ApiOperation(value = "Return a single user.",
    responseClass = "User", httpMethod = "GET")
  def findById(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.findById(id) match {
      case Some(x) => Ok(Json.toJson(x))
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
      case Some(x) => Ok(Json.toJson(x))
      case None => BadRequest("no user found with that email.")
    }
  }

  @ApiOperation(value = "Edit Name Field.",
    responseClass = "None", httpMethod = "GET")
  def editField(email: String, field: String, fieldText: Any) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.editField(email, field, fieldText)
    Ok(Json.obj("status" -> "success"))
  }

  @ApiOperation(value = "Edit a List.",
    responseClass = "None", httpMethod = "GET")
  def editList(email: String, field: String, fieldList: Any)= SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.editList(email, field, fieldList)
    Ok(Json.obj("status" -> "success"))
  }

  @ApiOperation(value = "Create a List.",
    responseClass = "None", httpMethod = "GET")
  def createList(email: String, field: String, fieldList: List[Any])= SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetUser)) { request =>
    users.createList(email, field, fieldList)
    Ok(Json.obj("status" -> "success"))
  }

}
