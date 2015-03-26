/**
 *
 */
package controllers

import api.{Permission, RequestWithUser, WithPermission}
import models.{User, UUID}
import play.api.mvc.{Action, BodyParser, Controller, Result, Results}
import securesocial.core.{Authenticator, Authorization, IdentityProvider, UserService, SecureSocial}
import securesocial.core.providers.utils.RoutesHelper

/**
 * Enforce authentication and authorization.
 * 
 * @author Luigi Marini
 * @author Rob Kooper
 *
 */
trait SecuredController extends Controller {
  def SecuredAction[A](p: BodyParser[A] = parse.anyContent, authorization: Authorization = WithPermission(Permission.Public), resourceId: Option[UUID] = None)(f: RequestWithUser[A] => Result) = Action(p) {
    implicit request => {
      // Only check secure social, no other auth methods are allowed
      val result = for (
        authenticator <- SecureSocial.authenticatorFromRequest;
        identity <- UserService.find(authenticator.identityId) match {
          case Some(x: User) => Some(x)
          case _ => None
        }
      ) yield {
          Authenticator.save(authenticator.touch)
          authorization match {
            case auth: WithPermission => {
              if (auth.isAuthorized(identity, resourceId)) {
                f(RequestWithUser(Some(identity), request))
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
              f(RequestWithUser(None, request))
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
