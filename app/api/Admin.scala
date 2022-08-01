package api

import java.util.Date

import javax.inject.Inject
import models._
import org.apache.commons.lang3.StringEscapeUtils
import play.api.libs.concurrent.Akka
import play.api.mvc.Controller
import play.api.Play.current
import play.api.libs.json.Json.toJson
import play.api.templates.Html
import services._
import services.mongodb.MongoSalatPlugin
import play.api.Logger
import util.Mail

import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsString, JsUndefined, JsValue}

/**
 * Admin endpoints for JSON API.
 */
class Admin @Inject() (userService: UserService,
    datasets: DatasetService,
    collections: CollectionService,
    files: FileService,
    events: EventService,
    esqueue: ElasticsearchQueue) extends Controller with ApiController {

  /**
   * DANGER: deletes all data, keep users.
   */
  def deleteAllData(resetAll: Boolean) = ServerAdminAction { implicit request =>
    current.plugin[MongoSalatPlugin].map(_.dropAllData(resetAll))
    current.plugin[ElasticsearchPlugin].map(_.deleteAll())

    Ok(toJson("done"))
  }

  def submitAppearance = ServerAdminAction(parse.json) { implicit request =>
    (request.body \ "theme").asOpt[String] match {
      case Some(theme) => AppConfiguration.setTheme(theme)
    }
    (request.body \ "displayName").asOpt[String] match {
      case Some(displayName) => AppConfiguration.setDisplayName(displayName)
    }
    (request.body \ "welcomeMessage").asOpt[String] match {
      case Some(welcomeMessage) => AppConfiguration.setWelcomeMessage(welcomeMessage)
    }
    (request.body \ "googleAnalytics").asOpt[String] match {
      case Some(s) => AppConfiguration.setGoogleAnalytics(s)
    }
    Ok(toJson(Map("status" -> "success")))
  }

  def sensorsConfig = ServerAdminAction(parse.json) { implicit request =>
    (request.body \ "sensors").asOpt[String] match {
      case Some(sensors) => AppConfiguration.setSensorsTitle(sensors)
    }
    (request.body \ "sensor").asOpt[String] match {
      case Some(sensor) => AppConfiguration.setSensorTitle(sensor)
    }
    (request.body \ "parameters").asOpt[String] match {
      case Some(parameters) => AppConfiguration.setParametersTitle(parameters)
    }
    (request.body \ "parameter").asOpt[String] match {
      case Some(parameter) => AppConfiguration.setParameterTitle(parameter)
    }
    Ok(toJson(Map("status" -> "success")))
  }

  def updateConfiguration = ServerAdminAction(parse.json) { implicit request =>
    getValueString(request.body, "theme").foreach(AppConfiguration.setTheme(_))
    getValueString(request.body, "displayName").foreach(AppConfiguration.setDisplayName(_))
    getValueString(request.body, "welcomeMessage").foreach(AppConfiguration.setWelcomeMessage(_))
    getValueString(request.body, "googleAnalytics").foreach(AppConfiguration.setGoogleAnalytics(_))
    getValueString(request.body, "sensors").foreach(AppConfiguration.setSensorsTitle(_))
    getValueString(request.body, "sensor").foreach(AppConfiguration.setSensorTitle(_))
    getValueString(request.body, "parameters").foreach(AppConfiguration.setParametersTitle(_))
    getValueString(request.body, "parameter").foreach(AppConfiguration.setParameterTitle(_))
    getValueString(request.body, "amplitudeApikey").foreach(AppConfiguration.setAmplitudeApiKey(_))

    getValueString(request.body, "tosText").foreach { tos =>
      events.addEvent(Event(request.user.get, event_type = EventType.TOS_UPDATE.toString))
      AppConfiguration.setTermsOfServicesText(tos)
      request.user.foreach(u => userService.acceptTermsOfServices(u.id))
    }
    getValueString(request.body, "tosHtml") match {
      case Some(s) => AppConfiguration.setTermOfServicesHtml(s.toLowerCase == "true")
      case None => AppConfiguration.setTermOfServicesHtml(false)
    }
    Ok(toJson(Map("status" -> "success")))
  }

  private def getValueString(body: JsValue, key: String): Option[String] = {
    body \ key match {
      case x: JsUndefined => None
      case x: JsString => Some(x.value)
      case x: JsValue => Some(x.toString)
    }
  }

  def mail = UserAction(false)(parse.json) { implicit request =>
    val body = StringEscapeUtils.escapeHtml4((request.body \ "body").asOpt[String].getOrElse("no text"))
    val subj = (request.body \ "subject").asOpt[String].getOrElse("no subject")

    val htmlbody = if (!current.configuration.getBoolean("smtp.mimicuser").getOrElse(true)) {
      val sender = request.user match {
        case Some(u) => u.email.getOrElse("")
        case None => ""
      }
      "<html><body><p>" + body + "</p>" + views.html.emails.footer(sender) + "</body></html>"
    } else {
      "<html><body><p>" + body + "</p>" + views.html.emails.footer() + "</body></html>"
    }

    Mail.sendEmailAdmins(subj, request.user, Html(htmlbody))
    Ok(toJson(Map("status" -> "success")))
  }

  def users = ServerAdminAction(parse.json) { implicit request =>
    (request.body \ "active").asOpt[List[String]].foreach(list =>
      list.foreach(id =>
        userService.findById(UUID(id)) match {
          case Some(u: ClowderUser) => {
            if (u.status == UserStatus.Inactive) {
              userService.update(u.copy(status = UserStatus.Active))
              val subject = s"[${AppConfiguration.getDisplayName}] account activated"
              val body = views.html.emails.userActivated(u, active = true)(request)
              util.Mail.sendEmail(subject, request.user, u, body)
            }
          }
          case _ => Logger.error(s"Could not update user with id=${id}")
        }))
    (request.body \ "inactive").asOpt[List[String]].foreach(list =>
      list.foreach(id =>
        userService.findById(UUID(id)) match {
          case Some(u: ClowderUser) => {
            if (!(u.status == UserStatus.Inactive)) {
              userService.update(u.copy(status = UserStatus.Inactive))
              val subject = s"[${AppConfiguration.getDisplayName}] account deactivated"
              val body = views.html.emails.userActivated(u, active = false)(request)
              util.Mail.sendEmail(subject, request.user, u, body)
            }
          }
          case _ => Logger.error(s"Could not update user with id=${id}")
        }))
    (request.body \ "admin").asOpt[List[String]].foreach(list =>
      list.foreach(id =>
        userService.findById(UUID(id)) match {
          case Some(u: ClowderUser) if (u.status == UserStatus.Active) => {

            userService.update(u.copy(status = UserStatus.Admin))
            val subject = s"[${AppConfiguration.getDisplayName}] admin access granted"
            val body = views.html.emails.userAdmin(u, admin = true)(request)
            util.Mail.sendEmail(subject, request.user, u, body)

          }
          case _ => Logger.error(s"Could not update user with id=${id}")
        }))
    (request.body \ "unadmin").asOpt[List[String]].foreach(list =>
      list.foreach(id =>
        userService.findById(UUID(id)) match {
          case Some(u: ClowderUser) if (u.status == UserStatus.Admin) => {
            userService.update(u.copy(status = UserStatus.Active))
            val subject = s"[${AppConfiguration.getDisplayName}] admin access revoked"
            val body = views.html.emails.userAdmin(u, admin = false)(request)
            util.Mail.sendEmail(subject, request.user, u, body)
          }

          case _ => Logger.error(s"Could not update user with id=${id}")
        }))
    Ok(toJson(Map("status" -> "success")))
  }

  def reindex = ServerAdminAction { implicit request =>
    val success = esqueue.queue("index_all")
    if (success) Ok(toJson(Map("status" -> "reindex successfully queued")))
    else BadRequest(toJson(Map("status" -> "reindex queuing failed, Elasticsearch may be disabled")))
  }

  def deleteIndex = ServerAdminAction { implicit request =>
    val success = esqueue.queue("delete_index")
    if (success) Ok(toJson(Map("status" -> "deindex successfully queued")))
    else BadRequest(toJson(Map("status" -> "deindex queuing failed, Elasticsearch may be disabled")))
  }
}
