package controllers

import play.api.Routes
import play.api.mvc.Action
import play.api.mvc.Controller
import api.Sections
import api.WithPermission
import api.Permission

/**
 * Main application controller.
 * 
 * @author Luigi Marini
 */
object Application extends SecuredController {
  
  /**
   * Main page.
   */
  def index = SecuredAction() { request =>
  	implicit val user = request.user
    Ok(views.html.index())
  }
  
  /**
   * Testing action.
   */
  def testJson = SecuredAction()  { implicit request =>
    Ok("{test:1}").as(JSON)
  }
  
    
  /**
   *  Javascript routing.
   */
  def javascriptRoutes = SecuredAction() { implicit request =>
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        routes.javascript.Admin.test,
        routes.javascript.Admin.secureTest,
        routes.javascript.Admin.reindexFiles,
        routes.javascript.Tags.search,
        
        api.routes.javascript.Comments.comment,
        api.routes.javascript.Datasets.comment,
        api.routes.javascript.Datasets.tag,
        api.routes.javascript.Datasets.removeTag,
        api.routes.javascript.Files.comment,
        api.routes.javascript.Files.tag,
        api.routes.javascript.Previews.upload,
        api.routes.javascript.Previews.uploadMetadata,
        api.routes.javascript.Sections.add,
        api.routes.javascript.Sections.comment,
        api.routes.javascript.Sections.tag
      )
    ).as(JSON) 
  }
  
}
