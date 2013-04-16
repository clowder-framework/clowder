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

/**
 * Comment
 * 
 * Based on http://docs.mongodb.org/manual/use-cases/storing-comments/
 * 
 * @author Rob Kooper
 *
 */
case class Comment(
    author: String,
    posted: Date,
    text: String,
    id: ObjectId = new ObjectId,
    replies: List[Comment] = List.empty
)

object Comment extends ModelCompanion[Comment, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Comment, ObjectId](collection = x.collection("comments")) {}
  }
  
//  def findDatasetComments(dataset_id : ObjectId, page_num : Integer = 1, page_size : Integer = 50) {
//    dao.find(MongoDBObject("dataset_id"->dataset_id))
//    	.sort(orderBy = MongoDBObject("full_slug" -> 1))
//    	.skip(page_num * page_size)
//    	.limit(page_size)
//    	.toList
//  }
//  
//  def findFileComments(file_id : ObjectId, page_num : Integer = 1, page_size : Integer = 50) {
//    dao.find(MongoDBObject("file_id"->file_id))
//    	.sort(orderBy = MongoDBObject("full_slug" -> 1))
//    	.skip(page_num * page_size)
//    	.limit(page_size)
//    	.toList
//  }
//
//  def addComment(dataset_id : Option[ObjectId], parent_slug : Option[ObjectId] = None, author : UserId, text : String) {
//    
//  }
}
