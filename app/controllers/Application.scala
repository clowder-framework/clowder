package controllers

import play.api.Routes
import play.api.mvc.Action
import play.api.mvc.Controller
import api.Sections
import api.Geostreams

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
  	implicit val user = request.user
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
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        routes.javascript.Admin.test,
        routes.javascript.Admin.secureTest,
        routes.javascript.Admin.reindexFiles,
        routes.javascript.Tags.tag,
        routes.javascript.Tags.search,
        routes.javascript.Files.comment,
        routes.javascript.Datasets.comment,
        routes.javascript.Datasets.tag,
        
        api.routes.javascript.Previews.upload,
        api.routes.javascript.Previews.uploadMetadata,
        api.routes.javascript.Sections.add,
        api.routes.javascript.Sections.tag,
        api.routes.javascript.Sections.comment,
        api.routes.javascript.Geostreams.searchSensors
      )
    ).as(JSON) 
  }
  
}
