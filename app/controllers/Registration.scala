package controllers

import javax.inject.Inject
import play.api.Play._
import play.api.i18n.Messages
import securesocial.controllers.Registration._
import securesocial.core._
import securesocial.core.providers.UsernamePasswordProvider
import services.{ UserService, SpaceService}
import models.{ UUID, User}
import play.api.mvc.{Result, Action,AnyContent}
import securesocial.core.providers.utils.{Mailer, GravatarHelper, RoutesHelper}
import securesocial.controllers.{ProviderController, Registration, TemplatesPlugin}
import play.api.Logger
import com.typesafe.plugin.use
import securesocial.core.providers.Token
/**
 * Registration class for overwritting securesocial.controllers.Registration when necessary
 */
class Registration @Inject()(spaces: SpaceService, users: UserService) extends SecuredController{


  def executeForToken(token: String, f: Token => Result): Result = {
    UserService.findToken(token) match {
      case Some(t) if !t.isExpired && t.isSignUp => f(t)
      case _ => Redirect(RoutesHelper.startSignUp()).flashing(Error -> Messages(InvalidLink))

    }
  }

  /**
   * Handles post from the sign up page. Checks if there is an invitation pending for a space. If so,
   * the person is added to the space with the assigned role in the invitation after signing up.
   * Minor Modification of securesocial.controllers.Registration.handleSignUp(token)
   */
  def handleSignUp(token: String) = Action { implicit request =>
    if(Registration.registrationEnabled) {
      executeForToken(token, { t =>
          Registration.form.bindFromRequest.fold (
            errors => {
              if (Logger.isDebugEnabled) {
                Logger.debug("[securesocial] errors " + errors)
              }
              BadRequest(use[TemplatesPlugin].getSignUpPage(request, errors, t.uuid))
            },
            info => {
              val id = if ( UsernamePasswordProvider.withUserNameSupport ) info.userName.get else t.email
              val identityId = IdentityId(id, providerId)
              val user = SocialUser(
                identityId,
                info.firstName,
                info.lastName,
                "%s %s".format(info.firstName, info.lastName),
                Some(t.email),
                GravatarHelper.avatarFor(t.email),
                AuthenticationMethod.UserPassword,
                passwordInfo = Some(Registry.hashers.currentHasher.hash(info.password))
              )
              val saved = UserService.save(user)
              val newuser = users.findByEmail(t.email)
              users.updateRepositoryPreferences(newuser.getOrElse(User.anonymous).id, Map("Purpose" -> "Testing-Only"))
              UserService.deleteToken(t.uuid)
              if ( UsernamePasswordProvider.sendWelcomeEmail ) {
                Mailer.sendWelcomeEmail(saved)
              }

              //Code from here to the end of the case is the difference between securesocial and this method. It checks
              // for an invitation pending to the space. If it finds invitation(s), it then adds the person to the space(s).
              spaces.getInvitationByEmail(t.email).map { invite =>
                users.findByEmail(invite.email) match {
                  case Some(user) => {
                    users.findRole(invite.role) match {
                      case Some(role) => {
                        spaces.addUser(user.id, role, invite.space)
                        spaces.removeInvitationFromSpace(UUID(invite.invite_id), invite.space)
                      }
                      case None => {
                        Redirect(RoutesHelper.startSignUp).flashing(Registration.Error -> Messages("Error adding to the invited space. The role assigned doesn't exist"))
                      }
                    }
                  }
                }
              }

              val eventSession = Events.fire(new SignUpEvent(user)).getOrElse(session)
              if ( UsernamePasswordProvider.signupSkipLogin ) {
                ProviderController.completeAuthentication(user, eventSession).flashing(Success -> Messages(SignUpDone))
              } else {
                Redirect(onHandleSignUpGoTo).flashing(Success -> Messages(SignUpDone)).withSession(eventSession)
              }
            }
          )
        })
      }

    else NotFound(views.html.defaultpages.notFound.render(request, None))

    }
  }
