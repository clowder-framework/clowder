/**
 *
 */
package controllers

import api.{Permission, RequestWithUser, WithPermission}
import models.{User, UUID}
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
  def SecuredAction[A](p: BodyParser[A] = parse.anyContent, authorization: Authorization = WithPermission(Permission.Public), resourceId: Option[UUID] = None)(f: RequestWithUser[A] => Result) = Action(p) {
    implicit request => {
      val user = SecureSocial.currentUser(request) match {
        case x: Option[User] => x
        case _ => None
      }
      if (authorization.isInstanceOf[WithPermission] && authorization.asInstanceOf[WithPermission].isAuthorized(user, resourceId)) {
        f(RequestWithUser(user, request))
      } else if (authorization.isAuthorized(user.orNull)) {
        f(RequestWithUser(user, request))
      } else if(user.isDefined) {  //User logged in but not authorized, so redirect to 'not authorized' page
        Results.Redirect(routes.Authentication.notAuthorized())
      } else {   //User not logged in, so redirect to login page
        Results.Redirect(RoutesHelper.login().absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not authenticated.")
      }
    }
  }
}

