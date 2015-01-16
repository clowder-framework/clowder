/**
 *
 */
package controllers

import api.{Permission, RequestWithUser, WithPermission}
import models.UUID
import play.api.mvc.{Action, BodyParser, Controller, Result, Results}
import securesocial.core.providers.utils.RoutesHelper
import securesocial.core._

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
      SecureSocial.currentUser(request) match { // calls from browser
        case Some(identity) => {
          if (authorization.isInstanceOf[WithPermission]){
            if (authorization.asInstanceOf[WithPermission].isAuthorized(identity, resourceId)) {
              f(RequestWithUser(Some(identity), request))
            } else if(SecureSocial.currentUser.isDefined) {  //User logged in but not authorized, so redirect to 'not authorized' page
              Results.Redirect(routes.Authentication.notAuthorized)
            } else {   //User not logged in, so redirect to login page
              Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not authorized.")
            }
          } else if(SecureSocial.currentUser.isDefined) {  //User logged in but not authorized, so redirect to 'not authorized' page
            Results.Redirect(routes.Authentication.notAuthorized)
          } else {   //User not logged in, so redirect to login page
            Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not authenticated.")
          }
        }
        case None => {
          if (authorization.isAuthorized(null))
            f(RequestWithUser(None, request))
          else {
            if(SecureSocial.currentUser.isDefined){  //User logged in but not authorized, so redirect to 'not authorized' page
              Results.Redirect(routes.Authentication.notAuthorized)
            }
            else{   //User not logged in, so redirect to login page
              Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not authenticated.")
            }
          }
        }
      }
    }
  }
}

