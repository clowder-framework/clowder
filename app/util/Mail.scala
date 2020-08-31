package util

import akka.actor.ActorSystem
import models.User
import play.api.Play.current
import play.api.libs.mailer.Email
import play.api.{Logger, Play}
import play.api.libs.mailer.MailerClient
import play.twirl.api.Html
import services.{DI, MailerService, UserService}

import scala.collection.immutable.Iterable
import scala.collection.mutable
import scala.concurrent.duration._

/**
  * Helper functions for sending emails.
  */
object Mail {

  val actorSystem = Play.current.injector.instanceOf[ActorSystem]
  val mailerClient = DI.injector.getInstance(classOf[MailerClient])


  /**
   * Send email to a single recipient
   */
  def sendEmail(subject: String, user: Option[User], recipient: String, body: Html) {
    sendEmail(subject, emailAddress(user), recipient::Nil, body)
  }

  /**
   * Send email to a single recipient
   */
  def sendEmail(subject: String, user: Option[User], recipient: Option[User], body: Html) {
    recipient.foreach(sendEmail(subject, user, _, body))
  }

  /**
   * Send email to a single recipient
   */
  def sendEmail(subject: String, user: Option[User], recipient: User, body: Html) {
    if (recipient.email.isDefined) {
      sendEmail(subject, emailAddress(user), emailAddress(Some(recipient))::Nil, body)
    }
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
    // this needs to be done otherwise the request object is lost and this will throw an
    // error.
    val (realfrom: String, realto: Iterable[String]) = if (!current.configuration.getBoolean("smtp.mimicuser").getOrElse(true)) {
      (emailAddress(None), recipients.toSet + from)
    } else {
      (from, recipients)
    }
    val text = body.body
    if (Logger.isDebugEnabled) {
      Logger.debug("Sending email to %s".format(realto.toList:_*))
      Logger.debug("Mail = [%s]".format(text))
    }
    import play.api.libs.concurrent.Execution.Implicits.defaultContext
    actorSystem.scheduler.scheduleOnce(1.seconds) {
      val mail = Email(subject, realfrom, realto.toList, Some(text))
      mailerClient.send(mail)
    }
  }

  private def getAdmins: List[String] = {
    val userService: UserService = DI.injector.getInstance(classOf[UserService])

    val admins = mutable.ListBuffer[String]()
    val seen = mutable.HashSet[String]()
    for (x <- userService.getAdmins) {
      if (x.email.isDefined && !seen(x.email.get)) {
        admins += emailAddress(Some(x))
        seen += x.email.get
      }
    }
    admins.toList
  }

  private def emailAddress(user: Option[User]): String = {
    val from = current.configuration.getString("smtp.from").getOrElse("devnull@ncsa.illinois.edu")
    val name = current.configuration.getString("smtp.fromName").getOrElse("Clowder")
    user match {
      case Some(u) => {
        u.email match {
          case Some(e) => s""""${u.fullName.getOrElse(User.anonymous.fullName.get).replace("\"", "")}" <${e}>"""
          case None => s""""${u.fullName.getOrElse(User.anonymous.fullName.get).replace("\"", "")}" <${from}>"""
        }
      }
      case None => s""""${name.replace("\"", "")}" <${from}>"""
    }
  }
}
