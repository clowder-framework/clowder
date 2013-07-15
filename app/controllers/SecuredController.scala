/**
 *
 */
package controllers

import play.api.mvc.Action
import play.api.mvc.Result
import play.api.mvc.Request
import play.api.Logger
import views.html.defaultpages.unauthorized
import play.api.mvc.Results.Unauthorized
import play.api.libs.Crypto
import org.apache.commons.codec.binary.Base64
import securesocial.core.providers.utils.DefaultPasswordValidator
import securesocial.core.SocialUser
import models.SocialUserDAO
import securesocial.core.providers.utils.BCryptPasswordHasher
import org.mindrot.jbcrypt.BCrypt
import securesocial.core.UserService
import securesocial.core.providers.UsernamePasswordProvider
import play.api.mvc.Session
import org.joda.time.DateTime
import securesocial.core.SecureSocial
import securesocial.core.UserId
import securesocial.core.Identity
import play.api.mvc.Controller
import securesocial.core.Authorization
import play.api.mvc.BodyParser
import securesocial.core.SecuredRequest
import securesocial.core.AuthenticationMethod

/**
 * @author Luigi Marini
 *
 */
trait SecuredController {

  val anonymous = new SocialUser(new UserId("anonymous", ""), "Anonymous", "User", "Anonymous User", None, None, AuthenticationMethod.UserPassword)
								  
  
   case class WithPermission(permission: String) extends Authorization {

	  def isAuthorized(user: Identity): Boolean = {
	    // lockpage
	    if (permission == "public") return true
	    
	    // check if we have a user
	    if (user == null) return false
	    
	    // role based
	    if (hasPermission(user.id, permission)) return true
	    
	    // don't enter
	    return false
	  }
	  
	  def hasPermission(userId: UserId, permission: String) = {
	    false
	  }
  }

   def SecuredAction[A](p: BodyParser[A], allowKey: Boolean = true, authorization: Authorization = WithPermission("public"))(f: SecuredRequest[A] => Result) = Action(p) {
	   
     
       implicit request => {
			request.headers.get("Authorization") match { // basic authentication
				case Some(authHeader) => {
					val header = new String(Base64.decodeBase64(authHeader.slice(6,authHeader.length).getBytes))
					val credentials = header.split(":")
					UserService.findByEmailAndProvider(credentials(0), UsernamePasswordProvider.UsernamePassword) match {
						case Some(identity) => {
							if (BCrypt.checkpw(credentials(1), identity.passwordInfo.get.password)) {
							  if (authorization.isAuthorized(identity))
								f(SecuredRequest(identity, request))
						      else
						        Unauthorized(views.html.defaultpages.unauthorized())
							} else {
								Logger.debug("Password doesn't match")
								Unauthorized(views.html.defaultpages.unauthorized())
							}
						}
						case None => {
							Logger.debug("User not found")
							Unauthorized(views.html.defaultpages.unauthorized())
						}
					}
				}
				case None => {
					request.queryString.get("key") match { // token in url
						case Some(key) => {
							if (key.length > 0) {
								// TODO Check for key in database
								if (allowKey && key(0).equals("letmein")) {
								  if (authorization.isAuthorized(anonymous))
									f(SecuredRequest(anonymous, request))
							      else
							        Unauthorized(views.html.defaultpages.unauthorized())
								} else {
									Logger.debug("Key doesn't match")
									Unauthorized(views.html.defaultpages.unauthorized())
								}
							} else Unauthorized(views.html.defaultpages.unauthorized())
						}
						case None => {
							SecureSocial.currentUser(request) match { // calls from browser
								case Some(identity) => {
								  if (authorization.isAuthorized(identity))
									f(SecuredRequest(identity, request))
							      else
							        Unauthorized(views.html.defaultpages.unauthorized())
								}
								case None => {
								  if (authorization.isAuthorized(null))
									f(SecuredRequest(anonymous, request))
							      else
							    	Unauthorized(views.html.defaultpages.unauthorized())
							}
						}
					}
				}
			}
		}
	   }
   }
  
}