package services

import models.Comment

/**
 * Created by lmarini on 2/17/14.
 */
trait CommentService {

  def get(commentId: String): Option[Comment]

  def insert(comment: Comment): Option[String]

  def findCommentsByCommentId(id: String) : List[Comment]

  def findCommentsByDatasetId(id: String, asTree: Boolean=true): List[Comment]

  def findCommentsByFileId(id: String): List[Comment]

  def findCommentsBySectionId(id: String): List[Comment]

  def removeComment(c: Comment)
}
