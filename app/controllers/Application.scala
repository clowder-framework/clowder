package controllers

import play.api.Routes
import play.api.mvc.Action
import play.api.mvc.Controller

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
  
    
  /**
   *  Javascript routing.
   */
  def javascriptRoutes = Action { implicit request =>
    import routes.javascript._
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        Admin.test, Admin.secureTest, Admin.reindexFiles
      )
    ).as(JSON) 
  }
  
}
