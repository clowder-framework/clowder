package api

import javax.inject.Inject

import com.wordnik.swagger.annotations.ApiOperation
import models.{UUID, User, File, Dataset, Collection}
import play.api.libs.json._
import play.api.mvc.Action
import services.{UserService, FileService, DatasetService, CollectionService}

/**
 * API to interact with the users.
 *
 * @author Rob Kooper
 */
class Users @Inject()(
  users: UserService,
  files: FileService,
  datasets: DatasetService,
  collections: CollectionService) extends ApiController {

  /**
   * Returns a list of all users in the system.
   */
  @ApiOperation(value = "List all users in the system",
    responseClass = "User", httpMethod = "GET")
  def list() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.UserAdmin)) { request =>
    Ok(Json.toJson(users.list.map(userToJSON)))
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
  def follow(followeeUUID: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.LoggedIn)) { request =>
    implicit val user = request.user
    user match {
      case Some(loggedInUser) => {
        val followerUUID = loggedInUser.id
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
  def unfollow(followeeUUID: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.LoggedIn)) { request =>
    implicit val user = request.user
    user match {
      case Some(loggedInUser) => {
        val followerUUID = loggedInUser.id
        users.unfollowUser(followeeUUID, followerUUID)
        Ok(Json.obj("status" -> "success"))
      }
      case None => {
        Ok(Json.obj("status" -> "fail"))
      }
    }
  }

  @ApiOperation(value = "Fetch the top N recommendations",
    responseClass = "None", httpMethod = "GET")
  def getTopRecommendations(sourceID: UUID, sourceType: String, num: Int) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.LoggedIn)) { request =>
    val sourceFollowerIDs = loadFollowersFromTypedID(sourceID, sourceType)
    sourceFollowerIDs match {
      case Some(sourceFollowerIDs) => {
        implicit val user = request.user
          user match {
            case Some(loggedInUser) => {
              val excludeIDs = loggedInUser.followedEntities.map(typedId => typedId.id))
              users.followUser(followeeUUID, followerUUID)
        Ok(Json.obj("status" -> "success"))
      }
      case None => {
        Ok(Json.obj("status" -> "fail"))
      }
    }
        val results = users.getTopRecommendations(sourceFollowerIDs, num)
        Ok(Json.obj("status" -> "put ids in a json"))
        // TODO - wrap up recommended ids in a JSON
      }
      case None => {
        Ok(Json.obj("status" -> "put empty id list here"))
        // return empty JSON
      }
    }
  }

  // helper function for the above recommendations algo
  def loadFollowersFromTypedID(id: UUID, sourceType: String): Option[List[UUID]] = {
    sourceType match {
      case "user" => {
        users.findById(id) match {
          case Some(model) => Option(model.followers)
          case None => None
        }
      }
      case "file" => {
        files.get(id) match {
          case Some(model) => Option(model.followers)
          case None => None
        }
      }
      case "dataset" => {
        datasets.get(id) match {
          case Some(model) => Option(model.followers)
          case None => None
        }
      }
      case "collection" => {
        collections.get(id) match {
          case Some(model) => Option(model.followers)
          case None => None
        }
      }
      case _ => None
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
