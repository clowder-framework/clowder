package controllers

import java.util.UUID

import com.typesafe.plugin._
import models.User
import org.joda.time.DateTime
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.i18n.Messages
import play.api.Play
import play.api.templates.Html
import securesocial.controllers.TemplatesPlugin
import securesocial.core.providers.utils.Mailer
import securesocial.core.providers.{Token, UsernamePasswordProvider}
import services.{AppConfigurationService, AppConfiguration, UserService}
import javax.inject.Inject

import play.api.http.Status._
import play.api.mvc.{Action, Results}
import util.{Direction, Formatters, Mail}

/**
 * Manage users.
 */
class Users @Inject() (users: UserService, appConfig: AppConfigurationService) extends SecuredController {
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
    appConfig.incrementCount('users, 1)
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
                followedUsers = followedUsers.++(List((fuser.id, fuser.fullName, fuser.email.getOrElse(""), fuser.getAvatarUrl())))
              }
              case None =>
            }
        }

        Ok(views.html.users.followingUsers(followedUsers, clowderUser.fullName, prev, next, limit))

      }
      case None => InternalServerError("User not defined")
    }
  }

  /**
   *  Gets the users ordered by UserId.
   */
  def getUsers(when: String, id: String, limit: Int) = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(clowderUser) => {
        val nextPage = (when == "a")
        val dbusers: List[models.User] = if(id != "") {
          users.list(Some(id), nextPage, limit)
        } else {
          users.list(None, nextPage, limit)
        }

        val usersList = dbusers.map(usr => (usr.id, usr.fullName, usr.email.getOrElse(""), usr.getAvatarUrl()))

        //Check if there is a prev page
        val prev = if(dbusers.nonEmpty && id != "") {
          val ds = users.list(Some(dbusers.head.id.stringify), nextPage = false, 1)
          if(ds.nonEmpty && dbusers.head.id != ds.head.id) {
            dbusers.head.id.stringify
          } else {
            ""
          }
        } else {
          ""
        }
        val next = if(dbusers.nonEmpty) {
          val ds = users.list(Some(dbusers.last.id.stringify), nextPage=true, 1)
          if(ds.nonEmpty && ds.head.id != dbusers.last.id) {
            dbusers.last.id.stringify
          } else {
            ""
          }
        } else {
          ""
        }

        Ok(views.html.users.listUsers(usersList, prev, next, limit))
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
              followers = followers.++(List((uFollower.id, uFollower.fullName, ufEmail, uFollower.getAvatarUrl())))
            }
            case None =>
          }
        }

        Ok(views.html.users.followers(followers, clowderUser.fullName, clowderUser.id, prev, next, limit))

      }
      case None => InternalServerError("User not defined")
    }

  }

  def acceptTermsOfServices(redirect: Option[String]) = AuthenticatedAction { implicit request =>
    request.user match {
      case Some(user) => {
        users.acceptTermsOfServices(user.id)
        Results.Redirect(redirect.getOrElse(routes.Application.index().url), TEMPORARY_REDIRECT)
      }
      case None => InternalServerError("User not defined")
    }
  }

  def sendEmail(subject: String, from: String, recipient: String, body: String) = AuthenticatedAction { implicit request =>
    util.Mail.sendEmail(subject, from, List(recipient), Html(body))
    Ok("Successfully emailed "+recipient)
  }
}
