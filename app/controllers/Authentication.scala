package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import models.Credentials
import api.WithPermission
import api.Permission

/**
 * Login, logout, signup.
 * 
 * @author Luigi Marini
 *
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
  def login = Action {
    Ok(views.html.login(loginForm))
  }
  
  /**
   * Handle login submission.
   */
  def loginSubmit = Action { implicit request =>
    loginForm.bindFromRequest.fold(
      // Form has errors, redisplay it
      errors => BadRequest(views.html.login(errors)),
      
      // We got a valid User value, display the summary
      user => Ok("Login successfull")
    )
  }
  
  def notAuthorized = SecuredAction(authorization = WithPermission(Permission.Public)) { implicit request =>
    implicit val user = request.user
    Ok(views.html.notAuthorized())
  }
  
}