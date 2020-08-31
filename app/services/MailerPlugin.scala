package services

import javax.inject.Inject
import javax.mail._
import javax.mail.internet._
import play.api.{Application, Configuration, Logger}

class MailerPlugin @Inject()(application: Application, configuration: Configuration, logger: Logger) extends MailerService {

  val from = configuration.get[String]("smtp.from")
  val host = configuration.get[String]("smtp.host")
  val properties = System.getProperties()
  properties.setProperty("mail.smtp.host", host)

  //SSL or regular SMTP
  var port = configuration.get[Int]("smtp.port")
  if (configuration.get[Boolean]("smtp.ssl")) {
    if (port == 0)
      port = 465

    properties.setProperty("mail.smtp.socketFactory.port", port.toString)
    properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
  }
  else {
    if (port == 0)
      port = 25
  }
  properties.setProperty("mail.smtp.port", port.toString)

  val user = configuration.get[String]("smtp.user")

  if (!user.equals("")) {
    properties.setProperty("mail.smtp.auth", "true")
  }

  def sendMail(subscriberMail: String, html: String, subject: String): Boolean = {

    logger.debug("Sending mail to " + subscriberMail)

    //Authenticate if needed
    var session = Session.getDefaultInstance(properties)
    if (!user.equals("")) {
      session = Session.getInstance(properties,
        new javax.mail.Authenticator() {
          override def getPasswordAuthentication(): PasswordAuthentication = {
            new PasswordAuthentication(user, configuration.get[String]("smtp.password"))
          }
        })
    }

    try {
      val message = new MimeMessage(session)
      message.setFrom(new InternetAddress(from))
      message.addRecipient(Message.RecipientType.TO,
        new InternetAddress(subscriberMail))
      message.setSubject(subject)
      message.setContent(html, "text/html")
      Transport.send(message)

      logger.debug("Sent message successfully.")
    } catch {
      case msgex: MessagingException => {
        Logger.error(msgex.toString())
        return false
      }
    }
    true
  }


}