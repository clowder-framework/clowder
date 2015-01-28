package api

import javax.inject.Inject

import com.wordnik.swagger.annotations.{ApiOperation, Api}
import controllers.Utils
import models.UUID
import play.api.Play._
import play.api.libs.json.Json._
import services.{AdminsNotifierPlugin, SpaceService}

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 *
 * @author Luigi Marini
 * @author Rob Kooper
 *
 */
@Api(value = "/spaces", listingPath = "/api-docs.json/spaces", description = "Spaces are groupings of collections and datasets.")
class Spaces @Inject()(spaces: SpaceService) extends ApiController {

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
}
