/**
 *
 */
package controllers

import api.{Permission, RequestWithUser, WithPermission}
import models.{User, UUID}
import play.api.mvc.{Action, BodyParser, Controller, Result, Results}
import securesocial.core.{Authenticator, Authorization, UserService, SecureSocial}

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
          //This block if an identity has been found  
          Authenticator.save(authenticator.touch)
          authorization match {
            case auth: WithPermission => {
              if (auth.isAuthorized(identity, resourceId)) {
                f(RequestWithUser(Some(identity), request))
              } else {
            		//User logged in but does not have permission
            		Results.Redirect(routes.RedirectUtility.incorrectPermissions("You do not have the permissions that are required in order to view that location."))
            	}
            }
            case _ =>  {
              //User logged in, but it's a controller that has a different authorization?
              Results.Redirect(routes.Authentication.notAuthorized)
            }
          }
        }

      // return Result, or check anonymous permissions
      result.getOrElse {
        authorization match {
          case auth: WithPermission => {
            if (auth.isAuthorized(None, resourceId)) {
              f(RequestWithUser(None, request))
            } else {
              Results.Redirect(routes.RedirectUtility.authenticationRequiredMessage("Authorization is required to access that location. Please login to proceed.", request.uri))
            }
          }
          case _ => {              
              //The case where it's a controller that has a different authorization?
              Results.Redirect(routes.RedirectUtility.authenticationRequiredMessage("Authentication is required to access that location. Please login to proceed.", request.uri))
          }
        }
      }
    }
  }
}
