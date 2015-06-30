package api

import api.Permission.Permission
import models.ResourceRef
import org.apache.commons.codec.binary.Base64
import org.mindrot.jbcrypt.BCrypt
import play.api.Logger
import play.api.mvc._
import models.{User, UUID}
import securesocial.core.{Authorization, SecureSocial, UserService, Authenticator}
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.{Authenticator, SecureSocial, UserService}
import services.DI

import scala.concurrent.Future

/**
 * Action builders check permissions in API calls. When creating a new endpoint, pick one of the actions defined below.
 *
 * All functions will always resolve the usr and place the user in the request.user.
 *
 * UserAction: call the wrapped code, no checks are done
 * AuthenticatedAction: call the wrapped code iff the user is logged in.
 * ServerAdminAction: call the wrapped code iff the user is a server admin.
 * PermissionAction: call the wrapped code iff the user has the right permission on the reference object.
 *
 */
trait ApiController extends Controller {
  /** get user if logged in */
  def UserAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      block(userRequest)
    }
  }

  /**
   * Use when you want to require the user to be logged in on a private server or the server is public.
   */
  def PrivateServerAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      if (Permission.checkPrivateServer(userRequest.user)) {
        block(userRequest)
      } else {
        Future.successful(Unauthorized("Not authorized"))
      }
    }
  }

  /** call code iff user is logged in */
  def AuthenticatedAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      if (userRequest.user.isDefined) {
        block(userRequest)
      } else {
        Future.successful(Unauthorized("Not authorized"))
      }
    }
  }

  /** call code iff user is a server admin */
  def ServerAdminAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      if (Permission.checkServerAdmin(userRequest.user)) {
        block(userRequest)
      } else {
        Future.successful(Unauthorized("Not authorized"))
      }
    }
  }

  /** call code iff user has right permission for resource */
  def PermissionAction(permission: Permission, resourceRef: Option[ResourceRef] = None) = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      if (Permission.checkPermission(userRequest.user, permission, resourceRef)) {
        block(userRequest)
      } else {
        Future.successful(Unauthorized("Not authorized"))
      }
    }
  }

  /** Return user based on request object */
  def getUser[A](request: Request[A]): UserRequest[A] = {
    // API will check for the user in the following order:
    // 1) secure social, this allows the web app to make calls to the API and use the secure social user
    // 2) basic auth, this allows you to call the api with your username/password
    // 3) key, this will need to become better, right now it will only accept the one key, when using the
    //    key it will assume you are anonymous!
    // 4) anonymous access

    // is the user a superadmin (this should still check serverAdmin)
    val superAdmin = request.cookies.get("superAdmin").isDefined || request.headers.get("superAdmin").isDefined

    // 1) secure social, this allows the web app to make calls to the API and use the secure social user
    for (
      authenticator <- SecureSocial.authenticatorFromRequest(request);
      identity <- UserService.find(authenticator.identityId)
    ) yield {
      Authenticator.save(authenticator.touch)
      val user = DI.injector.getInstance(classOf[services.UserService]).findByIdentity(identity)
      return UserRequest(Some(identity), user, superAdmin && Permission.checkServerAdmin(Some(identity)), request)
    }

    // 2) basic auth, this allows you to call the api with your username/password
    request.headers.get("Authorization").foreach { authHeader =>
      val header = new String(Base64.decodeBase64(authHeader.slice(6, authHeader.length).getBytes))
      val credentials = header.split(":")
      UserService.findByEmailAndProvider(credentials(0), UsernamePasswordProvider.UsernamePassword).foreach { identity =>
        val user = DI.injector.getInstance(classOf[services.UserService]).findByIdentity(identity)
        if (BCrypt.checkpw(credentials(1), identity.passwordInfo.get.password)) {
          return UserRequest(Some(identity), user, superAdmin && Permission.checkServerAdmin(Some(identity)), request)
        }
      }
    }

    // 3) key, this will need to become better, right now it will only accept the one key, when using the
    //    key it will assume you are anonymous!
    request.queryString.get("key").foreach { key =>
      // TODO this needs to become more secure
      if (key.nonEmpty) {
        if (key.head.equals(play.Play.application().configuration().getString("commKey"))) {
          return UserRequest(Some(Permission.anonymous), None, superAdmin, request)
        }
      }
    }

    // 4) anonymous access
    UserRequest(None, None, superAdmin=false, request)
  }
}
