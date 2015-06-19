/**
 *
 */
package controllers

import api.Permission.Permission
import api.{Permission, UserRequest}
import models.ResourceRef
import play.api.mvc._
import securesocial.core.{Authenticator, SecureSocial, UserService}
import services.DI

import scala.concurrent.Future

/**
 * Action builders check permissions in controller calls. When creating a new endpoint, pick one of the actions defined below.
 *
 * All functions will always resolve the usr and place the user in the request.user.
 *
 * UserAction: call the wrapped code, no checks are done
 * AuthenticatedAction: call the wrapped code iff the user is logged in.
 * ServerAdminAction: call the wrapped code iff the user is a server admin.
 * PermissionAction: call the wrapped code iff the user has the right permission on the reference object.
 *
 */
trait SecuredController extends Controller {
  /** get user if logged in */
  def UserAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      block(getUser(request))
    }
  }

  /** call code iff user is logged in */
  def AuthenticatedAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val UserRequest =
        UserRequest match {
          case Some(_) => block(UserRequest)
          case None => Future.successful(Results.Redirect(routes.Authentication.notAuthorized()))
        }
    }
  }

  /** call code iff user is a server admin */
  def ServerAdminAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val UserRequest = getUser(request)
      if (Permission.checkServerAdmin(UserRequest.user)) {
        block(UserRequest)
      } else {
        Future.successful(Results.Redirect(routes.Authentication.notAuthorized()))
      }
    }
  }

  /** call code iff user has right permission for resource */
  def PermissionAction(permission: Permission, resourceRef: Option[ResourceRef] = None) = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val UserRequest = getUser(request)
      if (Permission.checkPermission(UserRequest.user, permission, resourceRef)) {
        block(UserRequest)
      } else {
        Future.successful(Results.Redirect(routes.Authentication.notAuthorized()))
      }
    }
  }

  /** Return user based on request object */
  def getUser[A](request: Request[A]): UserRequest[A] = {
    // controllers will check for user in the following order:
    // 1) secure social
    // 2) anonymous access

    // 1) secure social, this allows the web app to make calls to the API and use the secure social user
    for (
      authenticator <- SecureSocial.authenticatorFromRequest;
      identity <- UserService.find(authenticator.identityId)
    ) yield {
      Authenticator.save(authenticator.touch)
      val user = DI.injector.getInstance(classOf[services.UserService]).findByIdentity(identity)
      return UserRequest(Some(identity), user, request)
    }

    // 2) anonymous access
    UserRequest(None, None, request)
  }
}
