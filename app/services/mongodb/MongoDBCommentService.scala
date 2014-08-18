package services.mongodb

import models.{UUID, Comment}
import services.CommentService
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import play.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._

/**
 * Created by lmarini on 2/17/14.
 */
class MongoDBCommentService extends CommentService {

  def get(commentId: UUID): Option[Comment] = {
    Comment.findOneById(new ObjectId(commentId.stringify))
  }

  def insert(comment: Comment): Option[String] = {
    Comment.insert(comment).map(_.toString)
  }

  def findCommentsByCommentId(id: UUID) : List[Comment] = {
    Comment.dao.find(MongoDBObject("comment_id"->id)).map { comment =>
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
      var theComment = get(id)
      removeComment(theComment.get)
  }  

}


object Comment extends ModelCompanion[Comment, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Comment, ObjectId](collection = x.collection("comments")) {}
  }
}
