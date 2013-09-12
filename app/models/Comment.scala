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
    author: Identity,
    text: String,
    comment_id: Option[String] = None,
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

  def findCommentsByCommentId(id: String) : List[Comment] = {
    dao.find(MongoDBObject("comment_id"->id)).map { comment =>
      comment.copy(replies=findCommentsByCommentId(comment.id.toString))
    }.toList
  }

  def findCommentsByDatasetId(id: String, asTree: Boolean=true) : List[Comment] = {
    if (asTree) {
	    dao.find(("comment_id" $exists false) ++ ("dataset_id"->id)).map { comment =>
	      comment.copy(replies=findCommentsByCommentId(comment.id.toString))
	    }.toList
    } else {
      dao.find(MongoDBObject("dataset_id"->id)).toList
    }
  }

  def findCommentsByFileId(id: String) : List[Comment] = {
    dao.find(("comment_id" $exists false) ++ ("file_id"->id)).map { comment =>
      comment.copy(replies=findCommentsByCommentId(comment.id.toString))
    }.toList
  }

  def findCommentsBySectionId(id: String) : List[Comment] = {
    dao.find(("comment_id" $exists false) ++ ("section_id"->id)).map { comment =>
      comment.copy(replies=findCommentsByCommentId(comment.id.toString))
    }.toList
  }
}
