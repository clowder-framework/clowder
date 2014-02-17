package services.mongodb

import models.Comment
import services.CommentService

/**
 * Created by lmarini on 2/17/14.
 */
class MongoDBCommentService extends CommentService {

  def insert(comment: Comment): Option[String] = {
    Comment.insert(comment).map(_.toString)
  }
}
