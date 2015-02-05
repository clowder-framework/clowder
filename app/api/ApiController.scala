package api

import org.apache.commons.codec.binary.Base64
import org.mindrot.jbcrypt.BCrypt
import play.api.Logger
import play.api.mvc.Action
import play.api.mvc.BodyParser
import play.api.mvc.Controller
import play.api.mvc.Result
import models.UUID
import securesocial.core.{AuthenticationMethod, Authorization, IdentityId, SecureSocial, SocialUser, UserService}
import securesocial.core.providers.UsernamePasswordProvider
import services.DI

/**
 * New way to wrap actions for authentication so that we have access to Identity.
 *
 * @author Rob Kooper
 *
 */
trait ApiController extends Controller {
  val anonymous = new SocialUser(new IdentityId("anonymous", ""), "Anonymous", "User", "Anonymous User", None, None, AuthenticationMethod.UserPassword)

  def SecuredAction[A](p: BodyParser[A] = parse.json, authorization: Authorization = WithPermission(Permission.Public), resourceId: Option[UUID] = None)(f: RequestWithUser[A] => Result) = Action(p) {
    implicit request => {
      // API will check permissions in the following order:
      // 1) secure social, this allows the web app to make calls to the API and use the secure social user
      // 2) basic auth, this allows you to call the api with your username/password
      // 3) key, this will need to become better, right now it will only accept the one key, when using the
      //    key it will assume you are anonymous!

      val secureSocialUser = SecureSocial.currentUser(request)
      val basicAuth = request.headers.get("Authorization")
      val keyAuth = request.queryString.get("key")

      (secureSocialUser, basicAuth, keyAuth) match {
        case (Some(identity), _, _) => {
          val user = DI.injector.getInstance(classOf[services.UserService]).findByIdentity(identity))
          if (authorization.isInstanceOf[WithPermission]) {
            if (authorization.asInstanceOf[WithPermission].isAuthorized(identity, resourceId))
              f(RequestWithUser(Some(identity), user, request))
            else
              Unauthorized("Not authorized")
          } else {
            Unauthorized("Not authorized")
          }
        }

        case (None, Some(authHeader), _) => {
          val header = new String(Base64.decodeBase64(authHeader.slice(6, authHeader.length).getBytes))
          val credentials = header.split(":")
          UserService.findByEmailAndProvider(credentials(0), UsernamePasswordProvider.UsernamePassword) match {
            case Some(identity) => {
              val user = DI.injector.getInstance(classOf[services.UserService]).findByIdentity(identity))
              if (BCrypt.checkpw(credentials(1), identity.passwordInfo.get.password)) {
                if (authorization.isInstanceOf[WithPermission]) {
                  if (authorization.asInstanceOf[WithPermission].isAuthorized(identity, resourceId)) {
                    f(RequestWithUser(Some(identity), user, request))
                  } else {
                    if (SecureSocial.currentUser.isDefined) {  //User logged in but not authorized, so redirect to 'not authorized' page
                      Unauthorized("Not authorized")
                    } else {   //User not logged in, so redirect to login page
                      Unauthorized("Not authenticated")
                    }
                  }
                } else {
                  if (SecureSocial.currentUser.isDefined) {  //User logged in but not authorized, so redirect to 'not authorized' page
                    Unauthorized("Not authorized")
                  } else{   //User not logged in, so redirect to login page
                    Unauthorized("Not authenticated")
                  }
                }
              } else {
                Unauthorized("Not authenticated")
              }
            }
            case None => {
              Unauthorized("Not authenticated")
            }
          }
        }

        case (None, None, Some(key)) => {
          // use auth key, see if no user can access this
          // TODO this needs to become more secure
          if (key.length > 0) {
            if (key(0).equals(play.Play.application().configuration().getString("commKey"))) {
              if (authorization.isAuthorized(anonymous)) {
                f(RequestWithUser(Some(anonymous), None, request))
              }
              else
                Unauthorized("Not authorized")
            } else {
              Unauthorized("Not authenticated")
            }
          } else {
            Unauthorized("Not authenticated")
          }
        }

        case (None, None, None) => {
          // no auth, see if no user can access this
          if (authorization.isAuthorized(null))
            f(RequestWithUser(None, None, request))
          else {
            Logger.debug("ApiController - Authentication failure")
            //Modified to return a message specifying that authentication is necessary, so that
            //callers can handle it appropriately.
            Unauthorized("Not authenticated")
          }
        }
      }
    }
  }
}
