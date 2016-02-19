package controllers

import play.api.Logger
import play.api.mvc.Results
import securesocial.core.IdentityProvider
import securesocial.core.providers.utils.RoutesHelper

/**
 * Utility controller to be called, typically as a redirect, from the client side when an AJAX error is received,
 * or when there are errors with authentication or permissions within the normal controller flow.
 * 
 */
class Error extends SecuredController {

    /**
     * Default method when the failure is due to not being logged in.
     * 
     * Requires no args, provides the generic message "You must be logged in to perform that action.".
     * 
     */
    def authenticationRequired() = UserAction(needActive = false) { implicit request =>
        Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You must be logged in to perform that action.")
    }
    
    /**
     * Message specific method when the failure is due to not being logged in. Ideally, the original url 
     * will be set as a cookie so that securesocial can redirect back to the user's original page.
     * 
     * Client should pass in their own message to be displayed. If the message is null or empty, it will default to
     * the generic message "You must be logged in to perform that action.".
     *  
     *  @param msg A String that will be the specific error message passed to the login panel
     *  @param url The originating window href for the failed authentication
     */
    def authenticationRequiredMessage(msg: String, url: String) = UserAction(needActive = false) { implicit request =>
        Logger.trace("The authentication required message is " + msg)
        var errMsg = "You must be logged in to perform that action."
        var origUrlPresent = false
        if (msg != null && !msg.trim().equals("")) {
            errMsg = msg
        }
        
        Logger.trace("The specified url to redirect to is " + url)
        
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
    
    /**
     * Redirect request that comes in when a user is logged in but does not have the appropriate permissions for accessing a specific location.
     * 
     * Client may pass in their own message to be displayed. If the message is null or empty, it will default to 
     * the generic message "You do not have the permissions required to view that location."
     * 
     * @param msg A String that will be the specific error message passed to the view to display
     */
    def incorrectPermissions(msg: String) = UserAction(needActive = false) { implicit request =>
        Logger.trace("The incorrectPermissions message is " + msg)
        var errMsg = "You do not have the permissions required to view that location."
        if (msg != null && !msg.trim().equals("")) {
            errMsg = msg
        }        
        
        //Implicit user val necessary to the view in order to make sure that the system knows if there is a user logged in or 
        //not, for appropriate visual cues.
        implicit val user = request.user
        Results.Ok(views.html.noPermissions(errMsg))
    }

    def notActivated = UserAction(needActive=false) { implicit request =>
        implicit val user = request.user
        if (user.exists(_.active)) {
            Redirect(routes.Application.index())
        } else {
            Ok(views.html.error.accountNotActive())
        }
    }

    /**
     * Deny user request to access resource.
     */
    def notAuthorized(message: String, id: String, resourceType: String ) = UserAction(needActive = false) { implicit request =>
        implicit val user = request.user
        Ok(views.html.notAuthorized(message, id, resourceType))
    }
}