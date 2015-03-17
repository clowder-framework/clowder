package api

import java.util.Date
import javax.inject.Inject
import com.wordnik.swagger.annotations.{ApiOperation, Api}
import models.{UUID, ProjectSpace}
import play.api.Logger
import controllers.Utils
import play.api.Play._
import play.api.libs.json.Json._
import services.{AdminsNotifierPlugin, SpaceService}

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 *
 * @author Luigi Marini
 * @author Rob Kooper
 * @author Jong Lee
 *
 */
@Api(value = "/spaces", listingPath = "/api-docs.json/spaces", description = "Spaces are groupings of collections and datasets.")
class Spaces @Inject()(spaces: SpaceService) extends ApiController {

  @ApiOperation(value = "Create a space",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  //TODO- Minimal Space created with Name and description. URLs are not yet put in
  def createSpace() = SecuredAction(authorization = WithPermission(Permission.CreateSpaces)) {
    request =>
      Logger.debug("Creating new space")
      val nameOpt = (request.body \ "name").asOpt[String]
      val descOpt = (request.body \ "description").asOpt[String]
      (nameOpt, descOpt) match{
        case(Some(name), Some(description)) =>{
          // TODO: add creator
          val userId = request.mediciUser.fold(UUID.generate)(_.id)
          val c = ProjectSpace(name = name, description = description, created = new Date(), creator = userId,
            homePage = List.empty, logoURL = None, bannerURL = None, usersByRole= Map.empty, collectionCount=0,
            datasetCount=0, userCount=0, metadata=List.empty)
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
  def removeSpace(spaceId: UUID) = SecuredAction(parse.anyContent,
    authorization = WithPermission(Permission.DeleteSpaces), resourceId = Some(spaceId)) { request =>
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
  def get(id: UUID) = SecuredAction(parse.anyContent,
    authorization = WithPermission(Permission.ShowSpace), resourceId = Some(id)) { request =>
    spaces.get(id) match {
      case Some(space) => Ok(spaceToJson(space))
      case None => BadRequest("Space not found")
    }
  }

  @ApiOperation(value = "List spaces",
    notes = "Retrieves information about spaces",
    responseClass = "None", httpMethod = "GET")
  def list() = SecuredAction(parse.anyContent,
    authorization = WithPermission(Permission.ListSpaces)) { request =>
    Ok(toJson(spaces.list().map(spaceToJson)))
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
  def addCollection(space: UUID) = SecuredAction(parse.json,
    authorization = WithPermission(Permission.EditCollection)) { request =>
    val collectionId = (request.body \ "collection_id").as[String]
    spaces.addCollection(UUID(collectionId), space)
    Ok(toJson("success"))
  }

  @ApiOperation(value = "Associate a dataset with a space",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def addDataset(space: UUID) = SecuredAction(parse.json,
    authorization = WithPermission(Permission.EditCollection)) { request =>
    val datasetId = (request.body \ "dataset_id").as[String]
    spaces.addDataset(UUID(datasetId), space)
    Ok(toJson("success"))
  }
}
