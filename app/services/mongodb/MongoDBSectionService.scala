package services.mongodb

import org.bson.types.ObjectId
import services.{CommentService, DatasetService, FileService, FolderService, PreviewService, SectionService}
import models._
import javax.inject.{Inject, Singleton}
import java.util.Date
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play._
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable.{ArrayBuffer, ListBuffer}

/**
 * USe MongoDB to store sections
 */
@Singleton
class MongoDBSectionService @Inject() (comments: CommentService, previews: PreviewService, files: FileService, datasets: DatasetService, folders: FolderService) extends SectionService {
  
  def listSections(): List[Section] = {
    SectionDAO.findAll.toList
  }

  def addTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String]) : List[Tag] = {
    Logger.debug("Adding tags to section " + id + " : " + tags)

    var tagsAdded : ListBuffer[Tag] = ListBuffer.empty[Tag]

    val section = SectionDAO.findOneById(new ObjectId(id.stringify)).get
    val existingTags = section.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    val createdDate = new Date
    val maxTagLength = play.api.Play.configuration.getInt("clowder.tagLength").getOrElse(100)
    tags.foreach(tag => {
      val shortTag = if (tag.length > maxTagLength) {
        Logger.error("Tag is truncated to " + maxTagLength + " chars : " + tag)
        tag.substring(0, maxTagLength)
      } else {
        tag
      }
      // Only add tags with new values.
      if (!existingTags.contains(shortTag)) {
        val tagObj = models.Tag(name = shortTag, userId = userIdStr, extractor_id = eid, created = createdDate)
        tagsAdded += tagObj
        SectionDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $addToSet("tags" -> Tag.toDBObject(tagObj)), false, false, WriteConcern.Safe)
      }
    })
    tagsAdded.toList
  }

  def removeTags(id: UUID, tags: List[String]) {
    Logger.debug("Removing tags in section " + id + " : " + tags)
    val section = SectionDAO.findOneById(new ObjectId(id.stringify)).get
    val existingTags = section.tags.map(_.id.toString())
    Logger.debug("existingTags after user and extractor filtering: " + existingTags.toString)
    // Only remove existing tags.
    tags.intersect(existingTags).map { tag =>
      Logger.info(tag)
      SectionDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $pull("tags" -> MongoDBObject("_id" -> new ObjectId(tag))), false, false, WriteConcern.Safe)
    }
  }

  def get(id: UUID): Option[Section] = {
    SectionDAO.findOneById(new ObjectId(id.stringify))
  }

  def get(ids: List[UUID]): DBResult[Section] = {
    val objectIdList = ids.map(id => {
      new ObjectId(id.stringify)
    })
    val query = MongoDBObject("_id" -> MongoDBObject("$in" -> objectIdList))

    val found = SectionDAO.find(query).toList
    val notFound = ids.diff(found.map(_.id))
    if (notFound.length > 0)
      Logger.error("Not all section IDs found for bulk get request")
    return DBResult(found, notFound)
  }

  def findByFileId(id: UUID): List[Section] = {
    SectionDAO.find(MongoDBObject("file_id" -> new ObjectId(id.stringify))).sort(MongoDBObject("startTime" -> 1)).toList
  }

  def findByTag(tag: String, user: Option[User]): List[Section] = {
    var filter = MongoDBObject("tags.name" -> tag)
    if(!(configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public")){
      filter = buildTagFilter(user) ++ MongoDBObject("tags.name" -> tag)
    }
    SectionDAO.find(filter).toList
  }

  def removeAllTags(id: UUID) {
    SectionDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("tags" -> List()), false, false, WriteConcern.Safe)
  }

  def comment(id: UUID, comment: Comment) {
    SectionDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $addToSet("comments" -> Comment.toDBObject(comment)), false, false, WriteConcern.Safe)
  }

  def removeSection(s: Section) {
    for (preview <- previews.findBySectionId(s.id)) {
      previews.removePreview(preview)
    }
    for (comment <- comments.findCommentsBySectionId(s.id)) {
      comments.removeComment(comment)
    }
    SectionDAO.remove(MongoDBObject("_id" -> new ObjectId(s.id.stringify)))
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

  def setDescription(id: UUID, descr: String) {
	    SectionDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("description" -> Some(descr)), false, false, WriteConcern.Safe)
  }

  /**
   * Return a list of tags and counts found in sections
   */
  def getTags(user: Option[User]): Map[String, Long] = {
    val filter = MongoDBObject("tags" -> MongoDBObject("$not" -> MongoDBObject("$size" -> 0)))
    var tags = scala.collection.mutable.Map[String, Long]()
    SectionDAO.dao.find(buildTagFilter(user) ++ filter).foreach{ x =>
      x.tags.foreach{ t =>
        tags.put(t.name, tags.get(t.name).getOrElse(0L) + 1L)
      }
    }
    tags.toMap
  }

  private def buildTagFilter(user: Option[User]): MongoDBObject = {
    if (user.isDefined && user.get.superAdminMode)
      return MongoDBObject()

    val orlist = collection.mutable.ListBuffer.empty[MongoDBObject]

    // all sections where user is the author
    user.foreach{u => orlist += MongoDBObject("author._id" -> new ObjectId(u.id.stringify))}

    // Get all sections in all files in all datasets you have access to.
    val datasetsList = datasets.listUser(user)
    val foldersList = folders.findByParentDatasetIds(datasetsList.map(x => x.id))
    val fileIds = datasetsList.map(x => x.files) ++ foldersList.map(x => x.files)
    orlist += ("file_id" $in fileIds.flatten.map(x => new ObjectId(x.stringify)))

    // create orlist
    $or(orlist.map(_.asDBObject))
  }
  /**
   * Update thumbnail used to represent this section.
   */
  def updateThumbnail(sectionId: UUID, thumbnailId: UUID) {
    SectionDAO.update(MongoDBObject("_id" -> new ObjectId(sectionId.stringify)),
      $set("thumbnail_id" -> thumbnailId.stringify), false, false, WriteConcern.Safe)
   }

  /**
   * Get the list of spaces that the section (section --> file --> dataset --> [collection] --> space) belongs to
   */
  def getParentSpaces(querySectionId: UUID): ArrayBuffer[UUID] = {
    // Get section
    get(querySectionId) match {

      case Some(section) => {
        // Get file ID
        val fileId = section.file_id
        Logger.debug("File ID: " + fileId)

        // Get the list of datasets that the file is part of
        val datasetList = datasets.findByFileIdDirectlyContain(fileId).toList
        val spaceList = new ArrayBuffer[UUID]

        // Iterate through each dataset and get the IDs of spaces that it belongs to
        datasetList.foreach(dataset => {
          // Iterate through each space ID and add it to the spaceList
          dataset.spaces.foreach(space => {
            spaceList.+=(space)
          })
        })

        // Return the spaces (after removing duplicates) that the section is belonging to
        return spaceList.distinct

      }
      case None => {
        return ArrayBuffer.empty
      }
    }
    
  }
  
}

object SectionDAO extends ModelCompanion[Section, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Section, ObjectId](collection = x.collection("sections")) {}
  }
}
