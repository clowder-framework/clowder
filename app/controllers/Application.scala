package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import models._
import com.mongodb.casbah.Imports._
import java.io.File
import com.mongodb.casbah.gridfs.Imports._
import play.api.Play.current
import se.radley.plugin.salat._
import play.api.libs.iteratee.Enumerator
import java.io.PipedOutputStream
import java.io.PipedInputStream
import play.api.libs.iteratee.Iteratee
import repository.DBRegistry
import java.io.FileInputStream

/**
 * Main application controller.
 * 
 * @author Luigi Marini
 */
object Application extends Controller {
  
  /**
   * Main page.
   */
  def index = Action {
    Ok(views.html.index("Application online."))
  }
  
  /**
   * Login form.
   */
  val loginForm = Form(
    mapping(
      "email" -> nonEmptyText,
      "password" -> nonEmptyText
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
  
  /**
   * Search results.
   */
  def search(query: String) = Action {
    Ok(views.html.searchResults(query))
  }
  
  /**
   * Testing action.
   */
  def testJson = Action {
    Ok("{test:1}").as(JSON)
  }
  
}