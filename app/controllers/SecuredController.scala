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
import play.api.mvc.WrappedRequest
import play.api.mvc.PlainResult
import play.libs.Json
import play.mvc.Results.Redirect
import securesocial.core.providers.utils.RoutesHelper
import securesocial.core.IdentityProvider
import play.api.mvc.SimpleResult
import play.api.mvc.Results
import play.api.http.Status

 /**
  * A request that adds the User for the current call
  */
case class RequestWithUser[A](user: Option[Identity], request: Request[A]) extends WrappedRequest(request)

object Permission extends Enumeration {
	type Permission = Value
	val Public,					// Page is public accessible, i.e. no login needed 
		Admin,
		CreateDatasets,
		ListDatasets,
		ShowDataset,
		SearchDatasets,
		AddDatasetsMetadata,		
		CreateTags,
		CreateComments,
		CreateFiles,
		ListFiles,
		ShowFile,
		DownloadFiles = Value
}

 /**
 * @author Luigi Marini
 *
 */
 trait SecuredController {

 	val anonymous = new SocialUser(new UserId("anonymous", ""), "Anonymous", "User", "Anonymous User", None, None, AuthenticationMethod.UserPassword)

 	import Permission._

 	case class WithPermission(permission: Permission) extends Authorization {

 		def isAuthorized(user: Identity): Boolean = {
			// order is important
			(user, permission) match {
			  case (_, Public)       => return true
			  case (_, ListDatasets) => return true
			  case (_, ListFiles)    => return true
			  case (_, ShowDataset)  => return true
			  case (_, SearchDatasets)  => return true
			  case (_, AddDatasetsMetadata)  => return true
			  case (_, ShowFile)     => return true
			  case (null, _)         => return false
			  case (_, _)            => return true
			}

			// don't enter
			return false
		}
	}

  /**
   * A Forbidden response for ajax clients
   * TODO this should be removed and all calls using ajax should call api
   * @param request
   * @tparam A
   * @return
   */
  private def ajaxCallNotAuthenticated[A](implicit request: Request[A]): PlainResult = {
    Unauthorized("Not authenticated")
  }

  private def ajaxCallNotAuthorized[A](implicit request: Request[A]): PlainResult = {
    Unauthorized("Not authorized")
  }

	def SecuredAction[A](p: BodyParser[A], ajaxCall : Boolean = false, allowKey: Boolean = true, authorization: Authorization = WithPermission(Public))(f: RequestWithUser[A] => Result) = Action(p) {
		implicit request => {
			request.headers.get("Authorization") match { // basic authentication
				case Some(authHeader) => {
					val header = new String(Base64.decodeBase64(authHeader.slice(6,authHeader.length).getBytes))
					val credentials = header.split(":")
					UserService.findByEmailAndProvider(credentials(0), UsernamePasswordProvider.UsernamePassword) match {
						case Some(identity) => {
							if (BCrypt.checkpw(credentials(1), identity.passwordInfo.get.password)) {
								if (authorization.isAuthorized(identity))
									f(RequestWithUser(Some(identity), request))
								else
									if (ajaxCall)
										ajaxCallNotAuthorized
									else
										Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled))
											.flashing("error" -> "You are not authorized.")
							} else {
								Logger.debug("Password doesn't match")
								if (ajaxCall)
									ajaxCallNotAuthenticated
								else
									Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled))
										.flashing("error" -> "Username/password are not valid.")
							}
						}
						case None => {
							Logger.debug("User not found")
							if (ajaxCall)
								ajaxCallNotAuthenticated
							else
								Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled))
										.flashing("error" -> "Username/password are not valid.")
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
										f(RequestWithUser(Some(anonymous), request))
									else
										if (ajaxCall)
											ajaxCallNotAuthorized
										else
											Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled))
												.flashing("error" -> "You are not authorized.")
								} else {
									Logger.debug("Key doesn't match")
									if (ajaxCall)
										ajaxCallNotAuthenticated
									else
										Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled))
											.flashing("error" -> "Username/password are not valid.")
								}
							} else
								if (ajaxCall)
									ajaxCallNotAuthenticated
								else
									Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled))
											.flashing("error" -> "Username/password are not valid.")
						}
						case None => {
							SecureSocial.currentUser(request) match { // calls from browser
								case Some(identity) => {
									if (authorization.isAuthorized(identity))
										f(RequestWithUser(Some(identity), request))
									else
										if (ajaxCall)
											ajaxCallNotAuthorized
										else
											Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled))
												.flashing("error" -> "You are not authorized.")
								}
								case None => {
									if (authorization.isAuthorized(null))
										f(RequestWithUser(None, request))
									else
										if (ajaxCall)
											ajaxCallNotAuthorized
										else
											Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled))
												.flashing("error" -> "You are not logged in.")
								}
							}
						}
					}
				}
			}
		}
	}

}