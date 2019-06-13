package api

import models.{Event, UUID, File}
import play.api.Play._
import services._
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json.toJson
import javax.inject.Inject

import util.Mail
import play.api.templates.Html

import scala.util.{Failure, Success, Try}
import controllers.Utils


class Events @Inject() (events: EventService,
                        files: FileService,
                        datasets: DatasetService) extends ApiController {

  /*
   * Add a new event to the database
   */
  def addEvent(event: Event) = AuthenticatedAction {implicit request =>
      events.addEvent(event)
      Ok(toJson("added new event"))
  }

  def sendExceptionEmail() = UserAction(needActive = false)(parse.json) { implicit request =>
    val re = (request.body \ "badRequest").asOpt[String].getOrElse("Non-tracked request")
    val ex = (request.body \ "exceptions").asOpt[String].getOrElse("Non-tracked exceptions")
    val subject: String = "Exception from " + AppConfiguration.getDisplayName
    val body = Html("<p>URL: " + Utils.baseUrl(request) + "</p>\n" +
      "<p>Version: " + sys.props.getOrElse("build.version", default = "0.0.0") + "#"+sys.props.getOrElse("build.bamboo", default = "development") +
      " branch:" + sys.props.getOrElse("build.branch", default = "unknown") +" sha1:"+sys.props.getOrElse("build.gitsha1", default = "unknown")+"</p>\n" +
      "<p>Request: "+re+"</p>\n" +
      "<p>Error: "+ex+"</p>")
    val recipient: String = "opensource+clowder@ncsa.illinois.edu"
    Mail.sendEmail(subject, request.user, recipient, body)
    Ok(toJson("Send Email success"))
  }


}
