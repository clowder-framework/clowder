/**
 *
 */
package models

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
    tags: List[String] = List.empty
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

  // ---------- Tags related code starts ------------------
  def addTags(id: String, tags: List[String]) {
    // TODO: Having issue with "$each", so do multiple updates for now. Improve later.
    // Remove leading and trailing spaces, and reduce multiple continuous spaces to one single space.
    val tags1 = tags.map(t => t.trim().replaceAll("\\s+", " "))
    Logger.debug("tag: tags: " + tags + ".  After removing extra spaces, tags1: " + tags1)
    tags1.foreach(tag => {
      dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("tags" -> tag), false, false, WriteConcern.Safe)
    })
  }

  def removeTags(id: String, tags: List[String]) {
    val tags1 = tags.map(t => t.trim().replaceAll("\\s+", " "))
    Logger.debug("removeTags: tags: " + tags + ".  After removing extra spaces, tags1: " + tags1)
    dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $pullAll("tags" -> tags1), false, false, WriteConcern.Safe)
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