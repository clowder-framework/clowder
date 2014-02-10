/**
 *
 */
package models

import java.util.Date
import org.bson.types.ObjectId
import services.MongoSalatPlugin
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import MongoContext.context
import play.api.Play.current
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._

/**
 * A portion of a file.
 * 
 * @author Luigi Marini
 *
 */
case class Section (
    id: ObjectId = new ObjectId,
    file_id: ObjectId = new ObjectId,
    order: Int = -1,
    startTime: Option[Int] = None, // in seconds
    endTime: Option[Int] = None, // in seconds
    area: Option[Rectangle] = None,
    preview: Option[Preview] = None,
    tags: List[Tag] = List.empty
)

case class Rectangle (
    x: Double,
    y: Double,
    w: Double,
    h: Double
    
   
) {
  override def toString() = f"[ $x%.3f, $y%.3f, $w%.3f, $h%.3f ]"
}
    
object SectionDAO extends ModelCompanion[Section, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Section, ObjectId](collection = x.collection("sections")) {}
  }
  
  def findByFileId(id: ObjectId): List[Section] = {
    dao.find(MongoDBObject("file_id"->id)).sort(MongoDBObject("startTime"->1)).toList
  }
  
  def findByTag(tag: String): List[Section] = {
    dao.find(MongoDBObject("tags.name" -> tag)).toList
  }

  // ---------- Tags related code starts ------------------
  // Input validation is done in api.Files, so no need to check again.
  def addTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Adding tags to section " + id + " : " + tags)
    val section = SectionDAO.findOneById(new ObjectId(id)).get
    val existingTags = section.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    val createdDate = new Date
    tags.foreach(tag => {
      // Only add tags with new values.
      if (!existingTags.contains(tag)) {
        val tagObj = Tag(id = new ObjectId, name = tag, userId = userIdStr, extractor_id = eid, created = createdDate)
        dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("tags" -> Tag.toDBObject(tagObj)), false, false, WriteConcern.Safe)
      }
    })
  }

  def removeTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Removing tags in section " + id + " : " + tags + ", userId: " + userIdStr + ", eid: " + eid)
    val section = SectionDAO.findOneById(new ObjectId(id)).get
    val existingTags = section.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    Logger.debug("existingTags after user and extractor filtering: " + existingTags.toString)
    // Only remove existing tags.
    tags.intersect(existingTags).map { tag =>
      dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $pull("tags" -> MongoDBObject("name" -> tag)), false, false, WriteConcern.Safe)
    }
  }

  def removeAllTags(id: String) {
    dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $set("tags" -> List()), false, false, WriteConcern.Safe)
  }
  // ---------- Tags related code ends ------------------

  def comment(id: String, comment: Comment) {
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("comments" -> Comment.toDBObject(comment)), false, false, WriteConcern.Safe)
  }
  
  def removeSection(s: Section){
    for(preview <- PreviewDAO.findBySectionId(s.id)){
          PreviewDAO.removePreview(preview)
        }
    for(comment <- Comment.findCommentsBySectionId(s.id.toString())){
          Comment.removeComment(comment)
        }
    SectionDAO.remove(MongoDBObject("_id" -> s.id))    
  }
  
  
  
}