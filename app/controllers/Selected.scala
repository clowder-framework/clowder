package controllers

import javax.inject.Inject

import play.api.Logger
import play.api.mvc.Controller
import services.{FileService, SelectionService}

/**
 * Show selected datasets.
 */
class Selected @Inject()(selections: SelectionService, files: FileService) extends Controller with SecuredController {

  def get() = AuthenticatedAction { implicit request =>
    request.user match {
      case Some(identity) => {
        implicit val user = request.user
	      val datasets = selections.get(request.user.get.email.get) // TODO handle edge cases
	      Ok(views.html.selected(datasets.to[scala.collection.mutable.ListBuffer]))
      }
      case None => Logger.error("Error get user from request"); InternalServerError
    }
  }
}