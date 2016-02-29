package util

import models.User
import play.Logger
import play.api.libs.concurrent.Akka
import play.api.mvc.{AnyContent, Request, RequestHeader}
import play.api.templates.Html
import securesocial.core.providers.utils.Mailer
import com.typesafe.plugin._
import play.api.libs.concurrent.Execution.Implicits._
import services.{DI, UserService, AppConfiguration}
import scala.collection.immutable.Iterable
import scala.concurrent.duration._
import play.api.Play.current

/**
  * Helper functions for sending emails.
  */
object Mail {
  /**
   * Send email to a single recipient
   */
  def sendEmail(subject: String, from: String, recipient: String, body: Html) {
    sendEmail(subject, from, recipient::Nil, body)
  }

  /**
   * Send email to a single recipient
   */
  def sendEmail(subject: String, user: Option[User], recipient: String, body: Html) {
    sendEmail(subject, emailAddress(user), recipient::Nil, body)
  }

  /**
   * Send email to all server admins
   */
  def sendEmailAdmins(subject: String, from: String, body: Html): Unit = {
    sendEmail(subject, from, getAdmins, body)
  }

  /**
   * Send email to all server admins
   */
  def sendEmailAdmins(subject: String, user: Option[User], body: Html): Unit = {
    sendEmail(subject, emailAddress(user), getAdmins, body)
  }

  /**
   * Send email to all recipient
   */
  def sendEmail(subject: String, user: Option[User], recipient: Iterable[String], body: Html) {
    sendEmail(subject, emailAddress(user), recipient, body)
  }

  /**
   * Send email to all recipients
   */
  def sendEmail(subject: String, from: String, recipients: Iterable[String], body: Html) {
    val text = body.body
    Akka.system.scheduler.scheduleOnce(1.seconds) {
      val mail = use[MailerPlugin].email
      mail.setSubject(subject)
      mail.setRecipient(recipients.toList:_*)
      mail.setFrom(from)

      // the mailer plugin handles null / empty string gracefully
      mail.send("", text)
    }
  }

  private def getAdmins: List[String] = {
    val userService: UserService = DI.injector.getInstance(classOf[UserService])
    val admins = AppConfiguration.getAdmins

    userService.list.filter(u => u.active && admins.contains(u.email.getOrElse(""))).map{ u =>
      emailAddress(Some(u))
    }.distinct
  }

  private def emailAddress(user: Option[User]): String = {
    user match {
      case Some(u) => {
        u.email match {
          case Some(e) => s""""${u.fullName}" <${e}>"""
          case None => Mailer.fromAddress
        }
      }
      case None => Mailer.fromAddress
    }
  }
}
