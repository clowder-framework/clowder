package api
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
import com.jolbox.bonecp.UsernamePassword
import securesocial.core.providers.UsernamePasswordProvider

/**
 * Secure API.
 * 
 * @author Luigi Marini
 *
 */
case class Authenticated[A](action: Action[A]) extends Action[A] {
  
  def apply(request: Request[A]): Result = {
    Logger.info("Checking authentication on API call")
    val keyFromHeader = request.headers.get("")
    request.headers.get("Authorization") match { // basic authentication
      case Some(authHeader) => {
        val header = new String(Base64.decodeBase64(authHeader.slice(6,authHeader.length).getBytes))
        val credentials = header.split(":")
        UserService.findByEmailAndProvider(credentials(0), UsernamePasswordProvider.UsernamePassword) match {
	          case Some(identity) => {
	            if (BCrypt.checkpw(credentials(1), identity.passwordInfo.get.password)) {
	               action(request)
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
              Logger.info("Found key " + key(0))
              // TODO Check for key in database
              if (key(0).equals("letmein")) {
    	        action(request)
              } else {
                Logger.debug("Key doesn't match")
                Unauthorized(views.html.defaultpages.unauthorized())
              }
            } else Unauthorized(views.html.defaultpages.unauthorized())
          }
          case None => Unauthorized(views.html.defaultpages.unauthorized())
        }
      }
    }
    
  }
  
  lazy val parser = action.parser
}