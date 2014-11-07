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

/**
 * Enforce authentication and authorization.
 * 
 * @author Luigi Marini
 * @author Rob Kooper
 *
 */
trait SecuredController extends Controller {
  val anonymous = new SocialUser(new IdentityId("anonymous", ""), "Anonymous", "User", "Anonymous User", None, None, AuthenticationMethod.UserPassword)

  def SecuredAction[A](p: BodyParser[A] = parse.anyContent, authorization: Authorization = WithPermission(Permission.Public))(f: RequestWithUser[A] => Result) = Action(p) {
    implicit request =>
      {
        request.headers.get("Authorization") match { // basic authentication
          case Some(authHeader) => {
            val header = new String(Base64.decodeBase64(authHeader.slice(6, authHeader.length).getBytes))
            val credentials = header.split(":")
            UserService.findByEmailAndProvider(credentials(0), UsernamePasswordProvider.UsernamePassword) match {
              case Some(identity) => {
                if (BCrypt.checkpw(credentials(1), identity.passwordInfo.get.password)) {
                  if (authorization.isAuthorized(identity))
                    f(RequestWithUser(Some(identity), request))
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
                if (authorization.isAuthorized(identity))
                  f(RequestWithUser(Some(identity), request))
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
                else {
                    //Modified to return a message specifying that authentication is necessary, so that 
                    //callers can handle it appropriately when specific ajax calls come in. Otherwise, redirect
                    //as normal to the login page with the generic message and behavior.                    
                    Logger.debug("SecuredController - Authentication failure")                           
                    
                    //Generate a pattern to match the uri's that contain at least one "/admin/" since those will not be
                    //navigation calls to the admin page. Also, ensure that other elements were being handled as normal.
                    //In the future, if other items need to be handled here, this can be extended with more patterns or 
                    //a pattern subclass can be generated.
                    val adminPattern = "(/admin/.+)".r                     
                    var uri = request.uri                    
                    
                    uri match {                        
                        case adminPattern(_) => {
                            Unauthorized("Authentication Required")
                        }
                        case _ => {
                            Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not logged in.")
                        }
                    }                                                                                                       
                }
              }
            }
          }
        }
      }
  }
}

