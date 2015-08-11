package controllers

import models.Credentials
import play.api.data.Forms._
import play.api.data._

/**
 * Login, logout, signup.
 */
object Authentication extends SecuredController {
  
  /**
   * Login form.
   */
  val loginForm = Form(
    mapping(
      "email" -> nonEmptyText,
      "password" -> text(minLength = 6)
    )(Credentials.apply)(Credentials.unapply)
   )
   
  
  /**
   * Login page.
   */
  def login = UserAction {
    Ok(views.html.login(loginForm))
  }
  
  /**
   * Handle login submission.
   */
  def loginSubmit = UserAction { implicit request =>
    loginForm.bindFromRequest.fold(
      // Form has errors, redisplay it
      errors => BadRequest(views.html.login(errors)),
      
      // We got a valid User value, display the summary
      user => Ok("Login successfull")
    )
  }

  /**
   * Deny user request to access resource.
   */
 def notAuthorized(message: String, id: String, resourceType: String ) = UserAction { implicit request =>
    implicit val user = request.user
    Ok(views.html.notAuthorized(message, id, resourceType))
  }
  
}