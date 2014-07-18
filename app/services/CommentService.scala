package services

import models.{UUID, Comment}

/**
 * Created by lmarini on 2/17/14.
 */
trait CommentService {

  def get(commentId: UUID): Option[Comment]

  def insert(comment: Comment): Option[String]

  def findCommentsByCommentId(id: UUID) : List[Comment]

  def findCommentsByDatasetId(id: UUID, asTree: Boolean=true): List[Comment]

  def findCommentsByFileId(id: UUID): List[Comment]

  def findCommentsBySectionId(id: UUID): List[Comment]

  def removeComment(c: Comment)
}
