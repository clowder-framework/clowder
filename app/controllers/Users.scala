package controllers

import _root_.java.util.UUID

import com.typesafe.plugin._
import org.joda.time.DateTime
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.i18n.Messages
import play.api.libs.concurrent.Akka
import play.api.mvc._
import play.api.templates.Html
import play.api.{Logger, Play}
import securesocial.controllers.TemplatesPlugin
import securesocial.core.UserService
import securesocial.core.providers.utils.Mailer
import securesocial.core.providers.{Token, UsernamePasswordProvider}
import services.AppConfiguration

/**
 * Manage users.
 */
object Users extends Controller {
  //Custom signup initiation code, to be used if config is set to send signup link emails to admins to forward to users
  
  val TokenDurationKey = securesocial.controllers.Registration.TokenDurationKey
  val DefaultDuration = securesocial.controllers.Registration.DefaultDuration
  val TokenDuration = Play.current.configuration.getInt(TokenDurationKey).getOrElse(DefaultDuration)
  
  val RegistrationEnabled = "securesocial.registrationEnabled"
  lazy val registrationEnabled = current.configuration.getBoolean(RegistrationEnabled).getOrElse(true)
  
  val onHandleStartSignUpGoTo = securesocial.controllers.Registration.onHandleStartSignUpGoTo  
  val Success = securesocial.controllers.Registration.Success
  val ThankYouCheckEmail = securesocial.core.providers.utils.Mailer.SignUpEmailSubject
  
  val SignUpEmailSubject = "mails.sendSignUpEmail.subject"
  
  val Email = "email"
  val startForm = Form (
    Email -> email.verifying(Constraint[String] {
      theEmail: String =>{
        if(theEmail.trim() != "")
          Valid
        else
          Invalid(ValidationError("Email must not be empty."))
      }
    })
  )
  
  def handleStartSignUp = Action { implicit request =>
    if (registrationEnabled) {
      startForm.bindFromRequest.fold (
        errors => {
          implicit val form = errors
          BadRequest(use[TemplatesPlugin].getStartSignUpPage)
        },
        email => {
          // check if there is already an account for this email address
          UserService.findByEmailAndProvider(email, UsernamePasswordProvider.UsernamePassword) match {
            case Some(user) => {
              // user signed up already, send an email offering to login/recover password
              Mailer.sendAlreadyRegisteredEmail(user)
            }
            case None => {
              val token = createToken(email, isSignUp = true)
              val theHTML = views.html.signUpEmailThroughAdmin(token._1, email)
              val admins = AppConfiguration.getAdmins
              for(admin <- admins) {
            	  sendEmail(Messages(SignUpEmailSubject), admin, theHTML)
              }
            }
          }
          Redirect(onHandleStartSignUpGoTo).flashing(Success -> play.Play.application().configuration().getString("messageOnStartRegistrationWithAdmin") , Email -> email)
        }
      )
    }
    else NotFound(views.html.defaultpages.notFound.render(request, None))
  }
  
  private def createToken(email: String, isSignUp: Boolean): (String, Token) = {
    val uuid = UUID.randomUUID().toString
    val now = DateTime.now

    val token = Token(
      uuid, email,
      now,
      now.plusMinutes(TokenDuration),
      isSignUp = isSignUp
    )
    UserService.save(token)
    (uuid, token)
  }
  
  def sendEmail(subject: String, recipient: String, body: Html) {
    import com.typesafe.plugin._
    import play.api.libs.concurrent.Execution.Implicits._

    import scala.concurrent.duration._

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
