package controllers

import _root_.java.util.UUID
import java.util.UUID

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
import securesocial.core.providers.utils.Mailer
import securesocial.core.providers.{Token, UsernamePasswordProvider}
import services.AppConfiguration
import javax.inject.Inject
import services.UserService
import play.api.mvc.Action
import util.Mail

/**
 * Manage users.
 */
class Users @Inject() (users: UserService) extends SecuredController {
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
          securesocial.core.UserService.findByEmailAndProvider(email, UsernamePasswordProvider.UsernamePassword) match {
            case Some(user) => {
              // user signed up already, send an email offering to login/recover password
              Mailer.sendAlreadyRegisteredEmail(user)
            }
            case None => {
              val token = createToken(email, isSignUp = true)
              val theHTML = views.html.signUpEmailThroughAdmin(token._1, email)
              val admins = AppConfiguration.getAdmins
              for(admin <- admins) {
            	  Mail.sendEmail(Messages(SignUpEmailSubject), admin, theHTML)
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
    securesocial.core.UserService.save(token)
    (uuid, token)
  }
  


  def getFollowing(index: Int, limit: Int) = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(clowderUser) => {
        var followedUsers: List[(models.UUID, String, String, String)] = List.empty
        val userIds = clowderUser.followedEntities.filter(_.objectType == "user")
        val userIdsToUse = userIds.slice(index*limit, (index+1)*limit)
        val prev = index -1
        val next = if(userIds.length > (index+1) * limit) {
          index + 1
        } else {
          -1
        }
        for (tidObject <-userIdsToUse) {
            val followedUser = users.get(tidObject.id)
            followedUser match {
              case Some(fuser) => {
                followedUsers = followedUsers.++(List((fuser.id, fuser.fullName, fuser.email.get, fuser.getAvatarUrl())))
              }
            }
        }

        Ok(views.html.users.followingUsers(followedUsers, clowderUser.fullName, clowderUser.id, prev, next, limit))

      }
      case None => InternalServerError("User not defined")
    }

  }


  def getFollowers(index: Int, limit: Int) = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(clowderUser) => {
        var followers: List[(models.UUID, String, String, String)] = List.empty
        val followersToUse = clowderUser.followers.slice(index*limit, (index+1)*limit)
        val prev = index-1
        val next = if(clowderUser.followers.length > (index+1) * limit) {
          index + 1
        } else {
          -1
        }
        for (followerID <- followersToUse) {
          val userFollower = users.findById(followerID)
          userFollower match {
            case Some(uFollower) => {
              val ufEmail = uFollower.email.getOrElse("")
              followers = followers.++(List((uFollower.id, ufEmail, uFollower.getAvatarUrl(), uFollower.fullName)))
            }
          }
        }

        Ok(views.html.users.followers(followers, clowderUser.fullName, clowderUser.id, prev, next, limit))

      }
      case None => InternalServerError("User not defined")
    }

  }
}
