package controllers

import play.api._
import play.api.mvc._

object Application extends Controller {
  
  def index = Action {
    Ok(views.html.index("Application online."))
  }
  
  def testJson = Action {
    Ok("{test:1}").as(JSON)
  }
  
}