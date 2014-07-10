package controllers

import play.mvc.Controller
import play.api.Logger
import play.api.mvc.Action
import play.api.mvc.Results
import securesocial.core.providers.utils.RoutesHelper
import securesocial.core.IdentityProvider

/**
 * Utility controller to be called, typically as a redirect, from the client side when an AJAX error is received.
 */
class RedirectUtility extends Controller {

    /**
     * Default method when the failure is due to not being logged in.
     * 
     * Requires no args, provides the generic message "You must be logged in to perform that action.".
     * 
     */
    def authenticationRequired() = Action { implicit request =>        
        Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You must be logged in to perform that action.")
    }
    
    /**
     * Message specific method when the failure is due to not being logged in.
     * 
     * Client should pass in their own message to be displayed. If the message is null or empty, it will default to
     * the generic message "You must be logged in to perform that action.".
     *  
     */
    def authenticationRequiredMessage(msg: String) = Action { implicit request =>
        Logger.info("---- Incoming message is " + msg + "--------")
        var errMsg = "You must be logged in to perform that action."
        if (msg != null && !msg.trim().equals("")) {
            errMsg = msg
        }
        
        Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> errMsg)
    }
}