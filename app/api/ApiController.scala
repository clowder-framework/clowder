package api

import play.api.Logger
import play.api.mvc.Action
import play.api.mvc.BodyParser
import play.api.mvc.Controller
import play.api.mvc.Result
import securesocial.core.AuthenticationMethod
import securesocial.core.Authorization
import securesocial.core.SecureSocial
import securesocial.core.SocialUser
import securesocial.core.IdentityId

/**
 * New way to wrap actions for authentication so that we have access to Identity.
 *
 * @author Rob Kooper
 *
 */
trait ApiController extends Controller {
  val anonymous = new SocialUser(new IdentityId("anonymous", ""), "Anonymous", "User", "Anonymous User", None, None, AuthenticationMethod.UserPassword)

  def SecuredAction[A](p: BodyParser[A] = parse.json, authorization: Authorization = WithPermission(Permission.Public))(f: RequestWithUser[A] => Result) = Action(p) {
    implicit request =>
      {
        request.queryString.get("key") match { // token in url
          case Some(key) => {
            if (key.length > 0) {
              // TODO Check for key in database
              if (key(0).equals(play.Play.application().configuration().getString("commKey"))) {
                if (authorization.isAuthorized(anonymous))
                  f(RequestWithUser(Some(anonymous), request))
                else
                  Unauthorized("Not authorized")
              } else {
                Logger.debug("Key doesn't match")
                Unauthorized("Not authenticated")
              }
            } else
              Unauthorized("Not authenticated")
          }
          case None => {
            SecureSocial.currentUser(request) match { // calls from browser
              case Some(identity) => {
                if (authorization.isAuthorized(identity))
                  f(RequestWithUser(Some(identity), request))
                else
                  Unauthorized("Not authorized")
              }
              case None => {
                if (authorization.isAuthorized(null))
                  f(RequestWithUser(None, request))
                else
                  Unauthorized("Not authorized")
              }
            }
          }
        }
      }
  }
}