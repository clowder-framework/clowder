package util

import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.templates.Html
import securesocial.core.providers.utils.Mailer
import com.typesafe.plugin._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import play.api.Play.current

/**
  * Helper functions for sending emails.
  */
object Mail {

  def sendEmail(subject: String, recipient: String, body: Html) {
    if ( Logger.isDebugEnabled ) {
      Logger.debug("Sending email to %s".format(recipient))
      Logger.debug("Title = %s".format(subject))
      Logger.debug("Mail = [%s]".format(body))
    }
    Akka.system.scheduler.scheduleOnce(1.seconds) {
      val mail = use[MailerPlugin].email
      mail.setSubject(subject)
      mail.setRecipient(recipient)
      mail.setFrom(Mailer.fromAddress)
      // the mailer plugin handles null / empty string gracefully
      mail.send("", body.body)
    }
  }
}
