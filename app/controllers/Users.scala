package controllers

import play.api.mvc._
import models.User
import org.bson.types.ObjectId
import play.api.Play.current
import play.api.data._
import play.api.data.Forms._
import play.api.data.Form
import play.api.data.validation.Constraint
import play.api.data.validation.Valid
import play.api.data.validation.Invalid
import play.api.data.validation.ValidationError
import com.typesafe.plugin._
import securesocial.controllers.TemplatesPlugin
import securesocial.core.UserService
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.providers.utils.Mailer
import securesocial.controllers.Registration
import securesocial.core.providers.Token
import _root_.java.util.UUID
import org.joda.time.DateTime
import play.api.Play
import play.api.i18n.Messages
import models.AppConfiguration
import play.api.templates.Html
import play.api.Logger
import play.api.libs.concurrent.Akka

/**
 * Manage users.
 * 
 * @author Luigi Marini
 */
object Users extends Controller {
  
  /**
   * List users.
   */
  def list() = Action {
    val users = User.findAll
    Ok(views.html.list(users))
  }

  /**
   * List users by country.
   */
  def listByCountry(country: String) = Action {
    val users = User.findByCountry(country)
    Ok(views.html.list(users))
  }

  /**
   * View user.
   */
  def view(id: String) = Action {
    User.findOneById(new ObjectId(id)).map( user =>
      Ok(views.html.user(user))
    ).getOrElse(NotFound)
  }

  /**
   * Create new user.
   */
  def create(username: String) = Action {
    val user = User(
      username = username,
      password = "1234"
    )
    User.save(user)
    Ok(views.html.user(user))
  }
  
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
              for(admin <- AppConfiguration.getDefault.get.admins){
            	  sendEmail(Messages(SignUpEmailSubject), admin, theHTML)
              }
            }
          }
          Redirect(onHandleStartSignUpGoTo).flashing(Success -> Messages(ThankYouCheckEmail), Email -> email)
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
  
  private def sendEmail(subject: String, recipient: String, body: Html) {
    import com.typesafe.plugin._
    import scala.concurrent.duration._
    import play.api.libs.concurrent.Execution.Implicits._

    if ( Logger.isDebugEnabled ) {
      Logger.debug("Sending registration email to %s".format(recipient))
      Logger.debug("Registration mail = [%s]".format(body))
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