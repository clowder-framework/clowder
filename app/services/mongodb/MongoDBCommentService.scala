package services.mongodb

import models.{DBResult, UUID, Comment}
import services.CommentService
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import play.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._

/**
 * Use MongoDB to store Comments
 */
class MongoDBCommentService extends CommentService {

  def get(commentId: UUID): Option[Comment] = {
    Comment.findOneById(new ObjectId(commentId.stringify))
  }

  def get(commentIds: List[UUID]): DBResult[Comment] = {
    val objectIdList = commentIds.map(id => {
      new ObjectId(id.stringify)
    })
    val query = MongoDBObject("_id" -> MongoDBObject("$in" -> objectIdList))

    val found = Comment.find(query).toList
    val notFound = commentIds.diff(found.map(f => f.id))
    if (notFound.length > 0)
      Logger.error("Not all file IDs found for bulk get request")
    return DBResult(found, notFound)
  }

  def insert(comment: Comment): Option[String] = {
    Comment.insert(comment).map(_.toString)
  }

  def findCommentsByCommentId(id: UUID) : List[Comment] = {
    Comment.dao.find(MongoDBObject("comment_id"->new ObjectId(id.stringify))).sort(orderBy = MongoDBObject("posted" -> -1)).map { comment =>
      comment.copy(replies=findCommentsByCommentId(comment.id))
    }.toList
  }

  def findCommentsByDatasetId(id: UUID, asTree: Boolean=true) : List[Comment] = {
    if (asTree) {
      Comment.dao.find(("comment_id" $exists false) ++ ("dataset_id"->new ObjectId(id.stringify))).map { comment =>
        comment.copy(replies=findCommentsByCommentId(comment.id))
      }.toList
    } else {
      Comment.dao.find(MongoDBObject("dataset_id"->id)).toList
    }
  }

  def findCommentsByFileId(id: UUID) : List[Comment] = {
    Comment.dao.find(("comment_id" $exists false) ++ ("file_id"->new ObjectId(id.stringify))).map { comment =>
      comment.copy(replies=findCommentsByCommentId(comment.id))
    }.toList
  }

  def findCommentsBySectionId(id: UUID) : List[Comment] = {
    Comment.dao.find(("comment_id" $exists false) ++ ("section_id"->new ObjectId(id.stringify))).map { comment =>
      comment.copy(replies=findCommentsByCommentId(comment.id))
    }.toList
  }

  def removeComment(c: Comment){
    for(reply <- findCommentsByCommentId(c.id)){
      removeComment(reply)
    }
    Comment.remove(MongoDBObject("_id" -> new ObjectId(c.id.stringify)))
  }

  /**
   * Implementation of the editComment method defined in the services/CommentService.scala trait.
   *
   * This implementation edits the comment by updating the "text" field in for the identified comment.
   */
  def editComment(id: UUID, commentText: String) {
      val result = Comment.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
          $set("text" -> commentText),
          false, false, WriteConcern.Safe);
  }

  /**
   * Implementation of the removeComment method defined in the services/CommentService.scala trait.
   *
   * This implementation removes the file by getting it by its identifier originally.
   */
  def removeComment(id: UUID) {
      val theComment = get(id)
      removeComment(theComment.get)
  }

  def updateAuthorFullName(userId: UUID, fullName: String) {
    Comment.update(MongoDBObject("author._id" -> new ObjectId(userId.stringify)),
      $set("author.fullName" -> fullName), false, true, WriteConcern.Safe)
  }

}


object Comment extends ModelCompanion[Comment, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Comment, ObjectId](collection = x.collection("comments")) {}
  }
}

