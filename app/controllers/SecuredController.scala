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
      Logger.debug("--- URI is " + request.uri)
      val result = for (
        authenticator <- SecureSocial.authenticatorFromRequest;
        identity <- UserService.find(authenticator.identityId)
      ) yield {
          //This block if an identity has been found
          Logger.debug(" - SecuredContoller - Identity found.")          
          Authenticator.save(authenticator.touch)
          val user = DI.injector.getInstance(classOf[services.UserService]).findByIdentity(identity)
          authorization match {
            case auth: WithPermission => {
            	if (auth.isAuthorized(identity, resourceId)) {
            	    //User logged in and authorized
            		f(RequestWithUser(Some(identity), user, request))
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
        		  f(RequestWithUser(None, None, request))
        	  } else {        		  
        		  //The case where not logged in and not authorized for the guest
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
