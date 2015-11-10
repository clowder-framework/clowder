package api

import models.ResourceRef
import services.ContextLDService
import javax.inject.Inject
import play.api.libs.json.Json._
import com.wordnik.swagger.annotations.ApiOperation
import play.api.Logger
import play.api.libs.json.JsString
import play.api.libs.json.Json

/**
 * API controller for Json-ld context service
 * 
 *@author Smruti Padhy 
 */
class ContextLD @Inject() (
  contextlds: ContextLDService ) extends ApiController{

  @ApiOperation(value = "Get the context for the metadata represented in Json-ld format using context id", httpMethod = "GET")
  def getContextById(id: models.UUID) = PermissionAction(Permission.ViewMetadata) { implicit request =>
    Logger.debug("context id : " + id)
    val contextld = contextlds.getContextById(id)
    contextld match {
      case Some(c) => Ok(Json.toJson(Map("@context" -> c)))
      case None => Ok("No Context Found")
    }
  }

  @ApiOperation(value = "Get the context for the metadata represented in Json-ld format using context name", httpMethod = "GET")
  def getContextByName(contextName: String) = PermissionAction(Permission.ViewMetadata) { implicit request =>
    val contextld = contextlds.getContextByName(contextName)
    contextld match {
      case Some(c) => Ok(Json.toJson(Map("@context" -> c)))
      case None => Ok("No Context Found")
    }
  }

  @ApiOperation(value = "Add context for the metadata represented in Json-ld format", httpMethod = "POST")
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

  @ApiOperation(value = "Delete context for the metadata represented in Json-ld format", httpMethod = "DELETE")
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