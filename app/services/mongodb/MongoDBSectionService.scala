package services.mongodb

import services.{PreviewService, SectionService, CommentService}
import models._
import javax.inject.{Inject, Singleton}
import models.Section
import java.util.Date
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._
import play.api.libs.json.{JsValue, Json}

/**
 * Created by lmarini on 2/17/14.
 */
@Singleton
class MongoDBSectionService @Inject() (comments: CommentService, previews: PreviewService) extends SectionService {

  def addTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Adding tags to section " + id + " : " + tags)
    val section = SectionDAO.findOneById(new ObjectId(id)).get
    val existingTags = section.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    val createdDate = new Date
    tags.foreach(tag => {
      // Only add tags with new values.
      if (!existingTags.contains(tag)) {
        val tagObj = Tag(id = new ObjectId, name = tag, userId = userIdStr, extractor_id = eid, created = createdDate)
        SectionDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("tags" -> Tag.toDBObject(tagObj)), false, false, WriteConcern.Safe)
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
      SectionDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $pull("tags" -> MongoDBObject("name" -> tag)), false, false, WriteConcern.Safe)
    }
  }

  def get(id: String): Option[Section] = {
    SectionDAO.findOneById(new ObjectId(id))
  }

  def findByFileId(id: String): List[Section] = {
    SectionDAO.find(MongoDBObject("file_id" -> new ObjectId(id))).sort(MongoDBObject("startTime" -> 1)).toList
  }

  def findByTag(tag: String): List[Section] = {
    SectionDAO.find(MongoDBObject("tags.name" -> tag)).toList
  }

  def removeAllTags(id: String) {
    SectionDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $set("tags" -> List()), false, false, WriteConcern.Safe)
  }

  def comment(id: String, comment: Comment) {
    SectionDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("comments" -> Comment.toDBObject(comment)), false, false, WriteConcern.Safe)
  }

  def removeSection(s: Section) {
    for (preview <- previews.findBySectionId(s.id.toString)) {
      previews.removePreview(preview)
    }
    for (comment <- comments.findCommentsBySectionId(s.id.toString())) {
      comments.removeComment(comment)
    }
    SectionDAO.remove(MongoDBObject("_id" -> s.id))
  }

  def insert(json: JsValue): String = {
    val id = new ObjectId
    val doc = com.mongodb.util.JSON.parse(Json.stringify(json)).asInstanceOf[DBObject]
    doc.getAs[String]("file_id").map(id => doc.put("file_id", new ObjectId(id)))
    doc.put("_id", id)
    Logger.debug("Adding a section: " + doc)
    SectionDAO.dao.collection.save(doc)
    id.toString
  }
}

object SectionDAO extends ModelCompanion[Section, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Section, ObjectId](collection = x.collection("sections")) {}
  }
}
