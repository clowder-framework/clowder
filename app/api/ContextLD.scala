package api

import services.ContextLDService
import javax.inject.Inject
import play.api.libs.json.Json._
import play.api.Logger
import play.api.libs.json.JsString
import play.api.libs.json.Json

/**
 * API controller for Json-ld context service
 *
 */
class ContextLD @Inject() (
  contextlds: ContextLDService ) extends ApiController{

  def getContextById(id: models.UUID) = PermissionAction(Permission.ViewMetadata) { implicit request =>
    Logger.debug("context id : " + id)
    val contextld = contextlds.getContextById(id)
    contextld match {
      case Some(c) => Ok(Json.toJson(Map("@context" -> c)))
      case None => Ok("No Context Found")
    }
  }

  def getContextByName(contextName: String) = PermissionAction(Permission.ViewMetadata) { implicit request =>
    val contextld = contextlds.getContextByName(contextName)
    contextld match {
      case Some(c) => Ok(Json.toJson(Map("@context" -> c)))
      case None => Ok("No Context Found")
    }
  }

  def addContext() = PermissionAction(Permission.EditMetadata)(parse.json) { implicit request =>
    request.user match {
      case Some(user) => {
        val context = request.body.\("@context")
        val contextName = request.body.\("context_name")
        val contextId = contextlds.addContext(contextName.as[JsString], context)
        Ok(toJson(Map("id" -> contextId.toString)))
      }
      case None => BadRequest(toJson("Not authorized."))
    }
  }

  def removeById(id: models.UUID) = PermissionAction(Permission.EditMetadata) { implicit request =>
    request.user match {
      case Some(user) => {
        Logger.debug("remove context: " + id)
        contextlds.removeContext(id)
        Ok("The context " + id + " has been removed")

      }
      case None => BadRequest("Not authorized.")
    }
  }
}
