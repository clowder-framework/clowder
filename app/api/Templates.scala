package api

import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import play.api.mvc.Action
import play.api.libs.json._
import play.api.libs.json._
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json.toJson
import javax.inject.{ Singleton, Inject }
import play.api.libs.json.{JsObject, JsValue}
import services.{TemplateService, EventService, UserService}
import models._
import java.util.Date
import scala.util.{Try, Success, Failure}


/**
  * Created by todd_n on 1/11/16.
  */
@Api(value = "/templates", listingPath = "/api-docs.json/templates", description = "user supplied templates of metadata key values")
@Singleton
class Templates @Inject() (userService: UserService, events: EventService, templateService : TemplateService) extends ApiController  {

  @ApiOperation(value = "Create a template",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def createTemplate() = AuthenticatedAction (parse.json) { implicit request =>
    val user = request.user
    var t : Template = null
    user match {
      case Some(identity) => {
        (request.body \ "keys").asOpt[String] match {
          case Some(keys) => {
            val name = request.body.asOpt[String].getOrElse("")
            t = Template(author = identity, created = new Date(),name = name,keys = keys.split(",").toList )

            templateService.insert(t) match {
              case Some(id) => {
                Ok(toJson(Map("id" -> id)))
              }
              case None => Ok(Map("status" -> "error"))
            }

          }
          case None => Ok("no keys")
        }

      }
      case None => Ok("no user")
    }


  }

  @ApiOperation(value = "list templates",
          notes = "",
        responseClass = "None", httpMethod = "GET")
  def listTemplates() = AuthenticatedAction { implicit request =>
    val user = request.user
    val templates = templateService.list()
    Ok(toJson(templates.toList))
  }

  @ApiOperation(value = "get template by id",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getTemplateById(id: UUID) = AuthenticatedAction { implicit request =>
    val user = request.user
    var t : Option[Template] = null
    templateService.get(id) match {
     case Some(template) => {
       Ok(toJson(template))
     }
     case None => Ok("no template found with id " + id)
   }
  }

  @ApiOperation(value = "get template by name",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getTemplateByName(name : String) = AuthenticatedAction { implicit request =>
    val user = request.user

    var t : Template = null


    Ok("not implemented")
  }

  def jsonTemplate(template : Template) : JsValue = {
    toJson(Map("id" -> template.id.toString,"keys"-> template.keys, "name" -> template.name))
  }

}
