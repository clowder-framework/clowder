package services

import models.{UUID, Comment}

/**
 * Service to manipulate comments in datasets and files.
 */
trait CommentService {

  def get(commentId: UUID): Option[Comment]

  def insert(comment: Comment): Option[String]

  def findCommentsByCommentId(id: UUID) : List[Comment]

  def findCommentsByDatasetId(id: UUID, asTree: Boolean=true): List[Comment]

  def findCommentsByFileId(id: UUID): List[Comment]

  def findCommentsBySectionId(id: UUID): List[Comment]

  def removeComment(c: Comment)
  
  /**
   * Service provided to actually edit a specific comment by id.
   * 
   * id: The identifier of the comment to edit, as a UUID.
   * commentText: The data to replace the comment text with, as a String.
   */
  def editComment(id: UUID, commentText: String)
  
   /**
   * Remove a comment by its identifier.
   * 
   * id: The identifier of the comment to remove, as a UUID.
   */
  def removeComment(id: UUID)
    
}

