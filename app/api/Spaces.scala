package api

import java.util.Date
import javax.inject.Inject

import com.wordnik.swagger.annotations.{ApiOperation, Api}
import models.{UUID, ProjectSpace, Collection}
import play.api.Logger
import play.api.libs.json.Json._
import services.SpaceService

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 *
 * @author Luigi Marini
 * @author Rob Kooper
 *
 */
@Api(value = "/spaces", listingPath = "/api-docs.json/spaces", description = "Spaces are groupings of collections and datasets.")
class Spaces @Inject()(spaces: SpaceService) extends ApiController {
  @ApiOperation(value = "Create a space",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def createSpace() = SecuredAction(authorization=WithPermission(Permission.CreateSpaces)) {
    request =>
      Logger.debug("Creating new space")
      val nameOpt = (request.body \ "name").asOpt[String]
      val descOpt = (request.body \ "description").asOpt[String]
      (nameOpt, descOpt) match{
        case(Some(name), Some(description)) =>{
          val c = ProjectSpace(name = name, description = description, created = new Date(), creator = (UUID.apply(""),""),
            homePage = List.empty, logoURL = None, bannerURL = None, usersByRole= Map.empty, collectionCount=0, datasetCount=0, userCount=0, metadata=List.empty)
          spaces.insert(c) match {
            case Some(id) => {
              Ok(toJson(Map("id" -> id)))
            }
            case None => Ok(toJson(Map("status" -> "error")))
          }

        }
        case (_,_) =>BadRequest(toJson("Missing required parameters"))
      }
  }

}
