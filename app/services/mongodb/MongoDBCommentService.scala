package services.mongodb

import models.{MongoContext, Comment}
import services.CommentService
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._

/**
 * Created by lmarini on 2/17/14.
 */
class MongoDBCommentService extends CommentService {

  def get(commentId: String): Option[Comment] = {
    Comment.findOneById(new ObjectId(commentId))
  }

  def insert(comment: Comment): Option[String] = {
    Comment.insert(comment).map(_.toString)
  }

  def findCommentsByCommentId(id: String) : List[Comment] = {
    Comment.dao.find(MongoDBObject("comment_id"->id)).map { comment =>
      comment.copy(replies=findCommentsByCommentId(comment.id.toString))
    }.toList
  }

  def findCommentsByDatasetId(id: String, asTree: Boolean=true) : List[Comment] = {
    if (asTree) {
      Comment.dao.find(("comment_id" $exists false) ++ ("dataset_id"->id)).map { comment =>
        comment.copy(replies=findCommentsByCommentId(comment.id.toString))
      }.toList
    } else {
      Comment.dao.find(MongoDBObject("dataset_id"->id)).toList
    }
  }

  def findCommentsByFileId(id: String) : List[Comment] = {
    Comment.dao.find(("comment_id" $exists false) ++ ("file_id"->id)).map { comment =>
      comment.copy(replies=findCommentsByCommentId(comment.id.toString))
    }.toList
  }

  def findCommentsBySectionId(id: String) : List[Comment] = {
    Comment.dao.find(("comment_id" $exists false) ++ ("section_id"->id)).map { comment =>
      comment.copy(replies=findCommentsByCommentId(comment.id.toString))
    }.toList
  }

  def removeComment(c: Comment){
    for(reply <- findCommentsByCommentId(c.id.toString())){
      removeComment(reply)
    }
    Comment.remove(MongoDBObject("_id" -> c.id))
  }
}


object Comment extends ModelCompanion[Comment, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Comment, ObjectId](collection = x.collection("comments")) {}
  }
}
