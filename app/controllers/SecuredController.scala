/**
 *
 */
package controllers

import api.{Permission, RequestWithUser, WithPermission}
import models.UUID
import play.api.Logger
import play.api.mvc.{Action, BodyParser, Controller, Result, Results}
import securesocial.core.providers.utils.RoutesHelper
import securesocial.core._
import services.DI

/**
 * Enforce authentication and authorization.
 * 
 * @author Luigi Marini
 * @author Rob Kooper
 *
 */
trait SecuredController extends Controller {
  val anonymous = new SocialUser(new IdentityId("anonymous", ""), "Anonymous", "User", "Anonymous User", None, None, AuthenticationMethod.UserPassword)

  def SecuredAction[A](p: BodyParser[A] = parse.anyContent, authorization: Authorization = WithPermission(Permission.Public), resourceId: Option[UUID] = None)(f: RequestWithUser[A] => Result) = Action(p) {
    implicit request => {
      // Only check secure social, no other auth methods are allowed
      val result = for (
        authenticator <- SecureSocial.authenticatorFromRequest;
        identity <- UserService.find(authenticator.identityId)
      ) yield {
          Authenticator.save(authenticator.touch)
          val user = DI.injector.getInstance(classOf[services.UserService]).findByIdentity(identity)
          authorization match {
            case auth: WithPermission => {
              if (auth.isAuthorized(identity, resourceId)) {
                f(RequestWithUser(Some(identity), user, request))
              } else {
                Results.Redirect(routes.Authentication.notAuthorized)
              }
            }
            case _ => Results.Redirect(routes.Authentication.notAuthorized)
          }
        }

      // return Result, or check anonymous permissions
      result.getOrElse {
        authorization match {
          case auth: WithPermission => {
            if (auth.isAuthorized(None, resourceId)) {
              f(RequestWithUser(None, None, request))
            } else {
              Results.Redirect(routes.Authentication.notAuthorized)
            }
          }
          case _ => Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not authenticated.")
        }
      }
    }
  }
}
