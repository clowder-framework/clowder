package controllers

import javax.inject.Inject
import play.api.Play._
import play.api.i18n.Messages
import securesocial.controllers.Registration._
import securesocial.core._
import securesocial.core.providers.UsernamePasswordProvider
import services.{SpaceService, UserService}
import models.UUID
import play.api.mvc.{Action, Result}
import securesocial.core.providers.utils.{ Mailer, RoutesHelper}
import securesocial.controllers.{ProviderController, Registration, TemplatesPlugin}
import play.api.Logger
import com.typesafe.plugin.use
import securesocial.core.providers.Token
import util.GravatarUtils

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

  def signUp(token: String) = Action { implicit request =>
    if ( play.Play.application().configuration().getBoolean("enableUsernamePassword") ) {
      Redirect(securesocial.core.providers.utils.RoutesHelper.signUp(token).absoluteURL(IdentityProvider.sslEnabled))
    } else {
      Redirect(securesocial.core.providers.utils.RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled))
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
              GravatarUtils.avatarFor(t.email),
              AuthenticationMethod.UserPassword,
              passwordInfo = Some(Registry.hashers.currentHasher.hash(info.password))
            )
            val saved = UserService.save(user)
            UserService.deleteToken(t.uuid)
            if ( UsernamePasswordProvider.sendWelcomeEmail ) {
              Mailer.sendWelcomeEmail(saved)
            }

            val eventSession = Events.fire(new SignUpEvent(user)).getOrElse(session)
            if ( UsernamePasswordProvider.signupSkipLogin ) {
              ProviderController.completeAuthentication(user, eventSession).flashing(Success -> Messages(SignUpDone))
            } else {
              // if registerThroughAdmins == true, then show the appropriate text
              if (play.Play.application().configuration().getBoolean("registerThroughAdmins")) {
                Redirect(onHandleSignUpGoTo).flashing(Success -> Messages(ThankYouCheckEmail)).withSession(eventSession)
              } else {
                Redirect(onHandleSignUpGoTo).flashing(Success -> Messages(SignUpDone)).withSession(eventSession)
              }
            }
          }
        )
      })
    }
    else NotFound(views.html.defaultpages.notFound.render(request, None))
  }
}
