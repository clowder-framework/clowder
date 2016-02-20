package api

import javax.inject.Inject

import models.{ClowderUser, UUID}
import play.api.mvc.Controller
import play.api.Play.current
import play.api.libs.json.Json.toJson
import play.api.templates.Html
import services.{UserService, ElasticsearchPlugin, AppConfiguration}
import services.mongodb.MongoSalatPlugin
import play.api.Logger
import util.Mail

/**
 * Admin endpoints for JSON API.
 *
 * @author Luigi Marini
 */
class Admin @Inject()(userService: UserService) extends Controller with ApiController {

  /**
   * DANGER: deletes all data, keep users.
   */
  def deleteAllData(resetAll: Boolean) = ServerAdminAction { implicit request =>
    current.plugin[MongoSalatPlugin].map(_.dropAllData(resetAll))
    current.plugin[ElasticsearchPlugin].map(_.deleteAll)

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
    (request.body \ "userAgreement").asOpt[String] match {
      case Some(userAgreement) => AppConfiguration.setUserAgreement(userAgreement)
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

  def mail = UserAction(false)(parse.json) { implicit request =>
    val body = (request.body \ "body").asOpt[String].getOrElse("no text")
    val subj = (request.body \ "subject").asOpt[String].getOrElse("no subject")

    Mail.sendEmailAdmins(subj, request.user, Html(body))
    Ok(toJson(Map("status" -> "success")))
  }

  def users = ServerAdminAction(parse.json) { implicit request =>
    (request.body \ "active").asOpt[List[String]].foreach(list =>
      list.foreach(id =>
        userService.findById(UUID(id)) match {
          case Some(u:ClowderUser) => {
            if (!u.active) {
              userService.update(u.copy(active=true))
              if (u.email.isDefined) {
                val subject = s"[${AppConfiguration.getDisplayName}] account activated"
                val body = views.html.emails.userActivated(u, active=true)(request)
                util.Mail.sendEmail(subject, request.user, u.email.get, body)
              }
            }
          }
          case None => Logger.error(s"Could not find user with id=${id}")
        }
      )
    )
    (request.body \ "inactive").asOpt[List[String]].foreach(list =>
      list.foreach(id =>
        userService.findById(UUID(id)) match {
          case Some(u:ClowderUser) => {
            if (u.active) {
              userService.update(u.copy(active = false))
              if (u.email.isDefined) {
                val subject = s"[${AppConfiguration.getDisplayName}] account deactivated"
                val body = views.html.emails.userActivated(u, active=false)(request)
                util.Mail.sendEmail(subject, request.user, u.email.get, body)
              }
            }
          }
          case _ => Logger.error(s"Could not find user with id=${id}")
        }
      )
    )
    (request.body \ "admin").asOpt[List[String]].foreach(list =>
      list.foreach(id =>
        userService.findById(UUID(id)) match {
          case Some(u) => {
            if (u.active && u.email.isDefined && !AppConfiguration.checkAdmin(u.email.get)) {
              AppConfiguration.addAdmin(u.email.get)
              if (u.email.isDefined) {
                val subject = s"[${AppConfiguration.getDisplayName}] admin access granted"
                val body = views.html.emails.userAdmin(u, admin=true)(request)
                util.Mail.sendEmail(subject, request.user, u.email.get, body)
              }
            }
          }
          case _ => Logger.error(s"Could not find user with id=${id}")
        }
      )
    )
    (request.body \ "unadmin").asOpt[List[String]].foreach(list =>
      list.foreach(id =>
        userService.findById(UUID(id)) match {
          case Some(u) if u.active && u.email.isDefined && AppConfiguration.checkAdmin(u.email.get) => {
            if (u.active && u.email.isDefined && AppConfiguration.checkAdmin(u.email.get)) {
              AppConfiguration.removeAdmin(u.email.get)
              if (u.email.isDefined) {
                val subject = s"[${AppConfiguration.getDisplayName}] admin access revoked"
                val body = views.html.emails.userAdmin(u, admin=false)(request)
                util.Mail.sendEmail(subject, request.user, u.email.get, body)
              }
            }
          }
          case _ => Logger.error(s"Could not find user with id=${id}")
        }
      )
    )
    Ok(toJson(Map("status" -> "success")))
  }
}
