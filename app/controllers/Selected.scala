package controllers

import play.api.mvc.Controller
import play.api.Logger
import api.WithPermission
import api.Permission
import javax.inject.Inject
import services.{FileService, SelectionService}

/**
 * Show selected datasets.
 * 
 * @author Luigi Marini
 *
 */
class Selected @Inject()(selections: SelectionService, files: FileService) extends Controller with SecuredController {

  def get() = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ListSelected)) { implicit request =>
    request.user match {
      case Some(identity) => {
        implicit val user = request.user
        
	      val datasets = selections.get(request.user.get.email.get) // TODO handle edge cases
	      val datasetsWithFiles = datasets map { dataset =>
	      val filesInDataset = dataset.files flatMap { f => files.get(f.id) }
	      Logger.debug("Files: " + filesInDataset)
	        dataset.copy(files=filesInDataset)
        }
        Logger.debug("Datasets: " + datasetsWithFiles)
	      Ok(views.html.selected(datasetsWithFiles))
      }
      case None => Logger.error("Error get user from request"); InternalServerError
    }
  }
}