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
  def list() = Action { request =>
    Ok(Json.toJson(users.list))
  }

  /**
   * Returns a single user based on the id specified.
   */
  @ApiOperation(value = "Return a single user.",
    responseClass = "User", httpMethod = "GET")
  def findById(id: UUID) = Action { request =>
    users.findById(id) match {
      case Some(x) => Ok(Json.toJson(x))
      case None => BadRequest("no user found with that id.")
    }
  }
}
