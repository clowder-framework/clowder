/**
 *
 */
package models

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

/**
 * Uploaded files.
 * 
 * @author Luigi Marini
 *
 */
case class File(
    id: ObjectId = new ObjectId, 
    path: Option[String] = None, 
    filename: String, 
    uploadDate: Date, 
    contentType: String,
    length: Long = 0,
    sections: List[Section] = List.empty,
    previews: List[Preview] = List.empty,
    comments: List[Comment] = List.empty,
    tags: List[String] = List.empty,
    metadata: Map[String, Any] = Map.empty
)

object FileDAO extends ModelCompanion[File, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[File, ObjectId](collection = x.collection("uploads.files")) {}
  }
  
  def get(id: String): Option[File] = {
    dao.findOneById(new ObjectId(id)) match {
      case Some(file) => {
        val previews = PreviewDAO.findByFileId(file.id)
        val sections = SectionDAO.findByFileId(file.id)
        val sectionsWithPreviews = sections.map { s =>
          val p = PreviewDAO.findOne(MongoDBObject("section_id"->s.id))
          s.copy(preview = p)
        }
        Some(file.copy(sections = sectionsWithPreviews, previews = previews))
      }
      case None => None
    }
  }
  
  
  //Not used yet
  def getMetadata(id: String): scala.collection.immutable.Map[String,Any] = {
		  dao.collection.findOneByID(new ObjectId(id)) match {
		  case None => new scala.collection.immutable.HashMap[String,Any]
		  case Some(x) => {
			  val returnedMetadata = x.getAs[DBObject]("metadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]].toMap
					  returnedMetadata
		  }
	  }
  }
  

  def findByTag(tag: String): List[File] = {
    dao.find(MongoDBObject("tags" -> tag)).toList
  }

  def tag(id: String, tag: String) { 
    dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)),  $addToSet("tags" -> tag), false, false, WriteConcern.Safe)
  }

  def comment(id: String, comment: Comment) {
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("comments" -> Comment.toDBObject(comment)), false, false, WriteConcern.Safe)
  }
  
  def getPreviewByType(file_id: String, content_type: String) {
    
  }
}
