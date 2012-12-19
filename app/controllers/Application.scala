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
import java.io.FileInputStream

/**
 * Main application controller.
 * 
 * @author Luigi Marini
 */
object Application extends Controller with securesocial.core.SecureSocial {
  
  /**
   * Main page.
   */
//  def index = Action { implicit request =>
//    Ok(views.html.index())
//  }
  def index = UserAwareAction { implicit request =>
    Ok(views.html.index())
  }
  
  /**
   * Testing action.
   */
  def testJson = Action {
    Ok("{test:1}").as(JSON)
  }
  
}