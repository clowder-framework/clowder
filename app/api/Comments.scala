package api

import org.bson.types.ObjectId
import play.Logger
import java.util.Date
import play.api.Play.current
import javax.inject.Inject
import services.{CommentService, DatasetService, ElasticsearchPlugin}
import models.UUID

/**
 * Comments on datasets.
 *
 * @author Rob Kooper
 */
class Comments @Inject()(datasets: DatasetService, comments: CommentService) extends ApiController {

  def comment(id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateComments)) {
    implicit request =>
      comments.get(id) match {
        case Some(parent) => {
          request.user match {
            case Some(identity) => {
              request.body.\("text").asOpt[String] match {
                case Some(text) => {
                  val comment = parent.copy(comment_id = Some(id), author = identity, text = text, posted = new Date())
                  comments.insert(comment)
                  if (parent.dataset_id.isDefined) {
                    datasets.get(parent.dataset_id.get) match {
                      case Some(dataset) => {
                        current.plugin[ElasticsearchPlugin].foreach {
                          _.indexDataset(dataset)
                        }
                      }
                      case None => Logger.error("Dataset not found: " + id)
                    }
                  }
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
        case None => BadRequest
      }
  }
}