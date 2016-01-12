package api

import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import play.api.mvc.Action
import play.api.libs.json._
import javax.inject.{ Singleton, Inject }
import play.api.libs.json.{JsObject, JsValue}
import services.{EventService, UserService}
import models._


/**
  * Created by todd_n on 1/11/16.
  */
@Api(value = "/templates", listingPath = "/api-docs.json/templates", description = "user supplied templates of metadata key values")
@Singleton
class Templates @Inject() (userService: UserService, events: EventService) extends ApiController  {

  @ApiOperation(value = "Create a template",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def createTemplate() = AuthenticatedAction (parse.json) { implicit request =>
    val user = request.user

    Ok("not implemented")
  }

  @ApiOperation(value = "list templates",
          notes = "",
        responseClass = "None", httpMethod = "GET")
  def listTemplates() = AuthenticatedAction { implicit request =>
    val user = request.user

    Ok("not implemented")
  }

  @ApiOperation(value = "get template by id",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getTemplateById(id: UUID) = AuthenticatedAction { implicit request =>
    val user = request.user

    Ok("not implemented")
  }

  @ApiOperation(value = "get template by name",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getTemplateByName(name : String) = AuthenticatedAction { implicit request =>
    val user = request.user

    Ok("not implemented")
  }

}
