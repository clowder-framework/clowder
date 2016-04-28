package api

import play.api.Logger
import play.api.Play.current
import models.{UUID, Collection, Event}
import play.api.mvc.RequestHeader
import services._
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json.toJson
import javax.inject.{ Singleton, Inject }
import util.Mail
import play.api.templates.Html

import scala.util.{Try, Success, Failure}
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import java.util.Date
import controllers.Utils

class Events @Inject() (events: EventService) extends ApiController {

  /*
   * Add a new event to the database
   */
     @ApiOperation(value = "Insert Event",
      notes = "Insert an Event into the Events Collection",
      responseClass = "None", httpMethod = "POST")
  def addEvent(event: Event) = AuthenticatedAction {implicit request =>
      events.addEvent(event)
      Ok(toJson("added new event"))
  }

  @ApiOperation(value = "Send Exception Email",
    notes = "Insert an Event into the Events Collection",
    responseClass = "None", httpMethod = "POST")
  def sendExceptionEmail() = UserAction(needActive = false)(parse.json) { implicit request =>
    val re = (request.body \ "badRequest").asOpt[String].getOrElse("Non-tracked request")
    val ex = (request.body \ "exceptions").asOpt[String].getOrElse("Non-tracked exceptions")
    val subject: String = "Exception from " + AppConfiguration.getDisplayName
    val body = Html("<p>Request: "+re+"</p><p>Error: "+ex.replace("   ", "\n")+"</p><p>Version: " + sys.props.getOrElse("build.version", default = "0.0.0") + "#"+sys.props.getOrElse("build.bamboo", default = "development")
    +" branch:" + sys.props.getOrElse("build.branch", default = "unknown") +" sha1:"+sys.props.getOrElse("build.gitsha1", default = "unknown")+"</p>")
    val recipient: String = "opensource+clowder@ncsa.illinois.edu"
    Mail.sendEmail(subject, request.user, recipient, body)
    Ok(toJson("Send Email success"))
  }
}
