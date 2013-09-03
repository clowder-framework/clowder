/**
 *
 */
package controllers

import play.api.mvc.Controller
import models.SelectedDAO
import play.api.Logger
import models.FileDAO

/**
 * Show selected datasets.
 * 
 * @author Luigi Marini
 *
 */
object Selected extends Controller with SecuredController {

  def get() = SecuredAction(parse.anyContent, allowKey=false, authorization=WithPermission(Permission.ListSelected)) { implicit request =>
    request.user match {
      case Some(identity) => {
        implicit val user = request.user
        
	    val datasets = SelectedDAO.get(request.user.get.email.get) // TODO handle edge cases
	    val datasetsWithFiles = datasets map { dataset =>
	      val files = dataset.files flatMap { f => FileDAO.get(f.id.toString) }
	      Logger.debug("Files: " + files)
	      dataset.copy(files=files)
        }
        Logger.debug("Datasets: " + datasetsWithFiles)
	    Ok(views.html.selected(datasetsWithFiles))
      }
      case None => Logger.error("Error get user from request"); InternalServerError
    }
  }
}