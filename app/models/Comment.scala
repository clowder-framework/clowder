package models

import java.util.Date
import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import services.MongoSalatPlugin
import com.mongodb.casbah.commons.MongoDBObject
import securesocial.core.Identity
import securesocial.core.UserId
import scala.util.Random
import java.util.Date
import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import services.MongoSalatPlugin
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._
import collection.JavaConverters._
import securesocial.core.Identity
import com.novus.salat.annotations.raw.Ignore

/**
 * Comment
 * 
 * Based on http://docs.mongodb.org/manual/use-cases/storing-comments/
 * 
 * @author Rob Kooper
 *
 */
case class Comment(
    parent_id: String,
    author: Identity,
    text: String,
    dataset_id: Option[String] = None,
    file_id: Option[String] = None,
    section_id: Option[String] = None,
    posted: Date = new Date(),
    id: ObjectId = new ObjectId,
    @Ignore replies: List[Comment] = List.empty
)

object Comment extends ModelCompanion[Comment, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Comment, ObjectId](collection = x.collection("comments")) {}
  }

  def findCommentsByParentId(id: String) : List[Comment] = {
    dao.find(MongoDBObject("parent_id"->id)).map { comment =>
      comment.copy(replies=findCommentsByParentId(comment.id.toString))
    }.toList
  }

  def findCommentsByDatasetId(id: String) : List[Comment] = {
    dao.find(MongoDBObject("dataset_id"->id)).map { comment =>
      comment.copy(replies=findCommentsByParentId(comment.id.toString))
    }.toList
  }

  def findCommentsByFileId(id: String) : List[Comment] = {
    dao.find(MongoDBObject("file_id"->id)).map { comment =>
      comment.copy(replies=findCommentsByParentId(comment.id.toString))
    }.toList
  }

  def findCommentsBySectionId(id: String) : List[Comment] = {
    dao.find(MongoDBObject("section_id"->id)).map { comment =>
      comment.copy(replies=findCommentsByParentId(comment.id.toString))
    }.toList
  }
  
  def removeComment(c: Comment){
    for(reply <- findCommentsByParentId(c.id.toString())){
          Comment.removeComment(reply)
        }
    Comment.remove(c)
  }
  
}
