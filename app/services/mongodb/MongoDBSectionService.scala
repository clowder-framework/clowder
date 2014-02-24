package services.mongodb

import services.{PreviewService, SectionService, CommentService}
import play.api.Logger
import java.util.Date
import models._
import com.mongodb.casbah.Imports._
import javax.inject.{Inject, Singleton}
import models.Section

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
}
