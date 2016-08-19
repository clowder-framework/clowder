package controllers

import play.api.Logger
import play.api.mvc.{Action}
import securesocial.core.{UserService, SecureSocial}

/**
  * Login class for checking if User is still logged through the securesocial.controllers.
  */
class Login extends SecuredController {
  def isLogged() = Action { implicit request =>
    val result = for (
      authenticator <- SecureSocial.authenticatorFromRequest(request);
      identity <- UserService.find(authenticator.identityId)
    ) yield {
      // we should be able to use the authenticator.timedOut directly but it never returns true
      identity
    }
    //Logger.debug("User's identity: " + result)

    result match {
      case Some(a) => Ok("yes")
      case None => Ok("no")
    }
  }
}