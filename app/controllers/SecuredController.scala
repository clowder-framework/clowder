/**
 *
 */
package controllers

import org.apache.commons.codec.binary.Base64
import org.mindrot.jbcrypt.BCrypt
import api.Permission
import api.RequestWithUser
import api.WithPermission
import play.api.Logger
import play.api.mvc.Action
import play.api.mvc.BodyParser
import play.api.mvc.Controller
import play.api.mvc.Result
import play.api.mvc.Results
import securesocial.core.AuthenticationMethod
import securesocial.core.Authorization
import securesocial.core.IdentityProvider
import securesocial.core.SecureSocial
import securesocial.core.SocialUser
import securesocial.core.UserService
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.providers.utils.RoutesHelper
import securesocial.core.IdentityId
import models.UUID

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
    implicit request =>
      {
        request.headers.get("Authorization") match { // basic authentication
          case Some(authHeader) => {
            val header = new String(Base64.decodeBase64(authHeader.slice(6, authHeader.length).getBytes))
            val credentials = header.split(":")
            UserService.findByEmailAndProvider(credentials(0), UsernamePasswordProvider.UsernamePassword) match {
              case Some(identity) => {
                if (BCrypt.checkpw(credentials(1), identity.passwordInfo.get.password)) {
                  if (authorization.isInstanceOf[WithPermission]){
                    if (authorization.asInstanceOf[WithPermission].isAuthorized(identity, resourceId))
                    	f(RequestWithUser(Some(identity), request))
                    else{
	                    if(SecureSocial.currentUser.isDefined){  //User logged in but not authorized, so redirect to 'not authorized' page
	                    	Results.Redirect(routes.Authentication.notAuthorized)
	                    }
	                    else{   //User not logged in, so redirect to login page
	                    	Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not authorized.")
	                    }
                    }	
                  }
                  else{
	                    if(SecureSocial.currentUser.isDefined){  //User logged in but not authorized, so redirect to 'not authorized' page
	                    	Results.Redirect(routes.Authentication.notAuthorized)
	                    }
	                    else{   //User not logged in, so redirect to login page
	                    	Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not authorized.")
	                    }
                    }
                } else {
                  Logger.debug("Password doesn't match")
                  Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "Username/password are not valid.")
                }
              }
              case None => {
                Logger.debug("User not found")
                Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "Username/password are not valid.")
              }
            }
          }
          case None => {
            SecureSocial.currentUser(request) match { // calls from browser
              case Some(identity) => {
                if (authorization.isInstanceOf[WithPermission]){
                  if (authorization.asInstanceOf[WithPermission].isAuthorized(identity, resourceId))
                	  f(RequestWithUser(Some(identity), request))
                  else
                		if(SecureSocial.currentUser.isDefined){  //User logged in but not authorized, so redirect to 'not authorized' page
	                    	Results.Redirect(routes.Authentication.notAuthorized)
	                    }
	                    else{   //User not logged in, so redirect to login page
	                    	Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not authorized.")
	                    }
                }
                else
                		if(SecureSocial.currentUser.isDefined){  //User logged in but not authorized, so redirect to 'not authorized' page
	                    	Results.Redirect(routes.Authentication.notAuthorized)
	                    }
	                    else{   //User not logged in, so redirect to login page
	                    	Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not authorized.")
	                    }
              }
              case None => {
                if (authorization.isAuthorized(null))
                  f(RequestWithUser(None, request))
                else
                  Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not logged in.")
              }
            }
          }
        }
      }
  }
}
