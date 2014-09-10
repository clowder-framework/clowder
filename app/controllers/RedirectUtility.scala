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
     * Message specific method when the failure is due to not being logged in. Ideally, the original url 
     * will be set as a cookie so that securesocial can redirect back to the user's original page.
     * 
     * Client should pass in their own message to be displayed. If the message is null or empty, it will default to
     * the generic message "You must be logged in to perform that action.".
     *  
     *  @param msg A String that that will be the specific error message passed to the login panel
     *  @param url The originating window href for the failed authentication
     */
    def authenticationRequiredMessage(msg: String, url: String) = Action { implicit request =>
        Logger.info("---- Incoming message is " + msg + "--------")
        var errMsg = "You must be logged in to perform that action."
        var origUrlPresent = false
        if (msg != null && !msg.trim().equals("")) {
            errMsg = msg
        }
        
        Logger.info("---- Incoming url is " + url + "-------")
        
        //If the url is present, set the session key
        if (url != null && !url.trim().equals("")) {
            origUrlPresent = true
        }
        
        if (origUrlPresent) {
            Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> errMsg).withSession("original-url" -> url)
        }
        else {
            Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> errMsg)
        }
    }
}