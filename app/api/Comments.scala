package api

import models.Comment
import org.bson.types.ObjectId
import play.Logger
import java.util.Date

object Comments extends ApiController {
  def comment(id:String) = SecuredAction(authorization=WithPermission(Permission.CreateComments)) { implicit request =>
    request.user match {
      case Some(identity) => {
	    request.body.\("text").asOpt[String] match {
	      case Some(text) => {
	        val comment = new Comment(id, identity, text)
	        Comment.save(comment)
	        Ok(comment.id.toString())
	      }
	      case None => {
	        Logger.error("no text specified.")
	        BadRequest
	      }
	    }
      }
      case None => BadRequest
    }
  }
}