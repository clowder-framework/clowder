package services

import models.Comment

/**
 * Created by lmarini on 2/17/14.
 */
trait CommentService {

  def insert(comment: Comment): Option[String]
}
