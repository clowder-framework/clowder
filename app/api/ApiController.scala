package api

import api.Permission.Permission
import models.{ClowderUser, ResourceRef, User, UserStatus}
import org.apache.commons.codec.binary.Base64
import org.mindrot.jbcrypt.BCrypt
import play.api.Logger
import play.api.mvc._
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.{Authenticator, SecureSocial, UserService}
import services.{AppConfiguration, DI}

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
  def UserAction(needActive: Boolean) = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      userRequest.user match {
        case Some(u) if needActive && (u.status == UserStatus.Inactive) => Future.successful(Unauthorized("Account is not activated"))
        case Some(u) if !AppConfiguration.acceptedTermsOfServices(u.termsOfServices) => Future.successful(Unauthorized("Terms of Service not accepted"))
        case _ => block(userRequest)
      }
    }
  }

  /**
   * Use when you want to require the user to be logged in on a private server or the server is public.
   */
  def PrivateServerAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      userRequest.user match {
        case Some(u) if (u.status == UserStatus.Inactive) => Future.successful(Unauthorized("Account is not activated"))
        case Some(u) if !AppConfiguration.acceptedTermsOfServices(u.termsOfServices) => Future.successful(Unauthorized("Terms of Service not accepted"))
        case Some(u) if u.superAdminMode || Permission.checkPrivateServer(userRequest.user) => block(userRequest)
        case None if Permission.checkPrivateServer(userRequest.user) => block(userRequest)
        case _ => Future.successful(Unauthorized("Not authorized"))
      }
    }
  }

  /** call code iff user is logged in */
  def AuthenticatedAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      userRequest.user match {
        case Some(u) if (u.status == UserStatus.Inactive) => Future.successful(Unauthorized("Account is not activated"))
        case Some(u) if !AppConfiguration.acceptedTermsOfServices(u.termsOfServices) => Future.successful(Unauthorized("Terms of Service not accepted"))
        case Some(u) => block(userRequest)
        case None => Future.successful(Unauthorized("Not authorized"))
      }
    }
  }

  /** call code iff user is a server admin */
  def ServerAdminAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      userRequest.user match {
        case Some(u) if (u.status == UserStatus.Inactive) => Future.successful(Unauthorized("Account is not activated"))
        case Some(u) if !AppConfiguration.acceptedTermsOfServices(u.termsOfServices) => Future.successful(Unauthorized("Terms of Service not accepted"))
        case Some(u) if u.superAdminMode || Permission.checkServerAdmin(userRequest.user) => block(userRequest)
        case _ => Future.successful(Unauthorized("Not authorized"))
      }
    }
  }

  /** call code iff user has right permission for resource */
  def PermissionAction(permission: Permission, resourceRef: Option[ResourceRef] = None, affectedResource: Option[ResourceRef] = None) = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      userRequest.user match {
        case Some(u) if (u.status == UserStatus.Inactive) => Future.successful(Unauthorized("Account is not activated"))
        case Some(u) if !AppConfiguration.acceptedTermsOfServices(u.termsOfServices) => Future.successful(Unauthorized("Terms of Service not accepted"))
        case Some(u) if u.superAdminMode || Permission.checkPermission(userRequest.user, permission, resourceRef) => block(userRequest)
        case Some(u) => {
          affectedResource match {
            case Some(resource) if Permission.checkOwner(u, resource) => block(userRequest)
            case _ => Future.successful(Unauthorized("Not authorized"))
           }
        }
        case None if Permission.checkPermission(userRequest.user, permission, resourceRef) => block(userRequest)
        case _ => Future.successful(Unauthorized("Not authorized"))
      }
    }
  }

  /**
   * Disable a route without having to comment out the entry in the routes file. Useful for when we want to keep the
   * code around but we don't want users to have access to it.
   */
  def DisabledAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      Future.successful(Unauthorized("Disabled"))
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

    val superAdmin = try {
      request.queryString.get("superAdmin").exists(_.exists(_.toBoolean)) || request.cookies.get("superAdmin").exists(_.value.toBoolean)
    } catch {
      case _: Throwable => false
    }

    // 1) secure social, this allows the web app to make calls to the API and use the secure social user
    for (
      authenticator <- SecureSocial.authenticatorFromRequest(request);
      identity <- UserService.find(authenticator.identityId)
    ) yield {
      Authenticator.save(authenticator.touch)
      val user = DI.injector.getInstance(classOf[services.UserService]).findByIdentity(identity) match {
        case Some(u: ClowderUser) if Permission.checkServerAdmin(Some(u)) => Some(u.copy(superAdminMode=superAdmin))
        case Some(u) => Some(u)
        case None => None
      }
      return UserRequest(user, request)
    }

    // 2) basic auth, this allows you to call the api with your username/password
    request.headers.get("Authorization").foreach { authHeader =>
      val header = new String(Base64.decodeBase64(authHeader.slice(6, authHeader.length).getBytes))
      val credentials = header.split(":")
      UserService.findByEmailAndProvider(credentials(0), UsernamePasswordProvider.UsernamePassword).foreach { identity =>
        val user = DI.injector.getInstance(classOf[services.UserService]).findByIdentity(identity)
        if (BCrypt.checkpw(credentials(1), identity.passwordInfo.get.password)) {
          val user = DI.injector.getInstance(classOf[services.UserService]).findByIdentity(identity) match {
            case Some(u: ClowderUser) if Permission.checkServerAdmin(Some(u)) => Some(u.copy(superAdminMode=superAdmin))
            case Some(u) => Some(u)
            case None => None
          }
          return UserRequest(user, request)
        }
      }
    }

    // 3) key, this will need to become better, right now it will only accept the one key, when using the
    //    key it will assume you are anonymous!
    request.queryString.get("key").foreach { key =>
      val userservice = DI.injector.getInstance(classOf[services.UserService])
      val commkey = play.Play.application().configuration().getString("commKey")
      key.foreach { realkey =>
        // check to see if this is the global key
        if (realkey == commkey) {
          return UserRequest(Some(User.anonymous.copy(superAdminMode=true)), request)
        }
        // check to see if this is a key for a specific user
        userservice.findByKey(realkey) match {
          case Some(u: ClowderUser) if Permission.checkServerAdmin(Some(u)) => {
            return UserRequest(Some(u.copy(superAdminMode = superAdmin)), request)
          }
          case Some(u) => {
            return UserRequest(Some(u), request)
          }
          case None => Logger.debug(s"key ${realkey} not associated with a user.")
        }
      }
    }

    // 4) anonymous access
    UserRequest(None, request)
  }
}
