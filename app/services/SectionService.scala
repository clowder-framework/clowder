package services

import models.{UUID, Section, Comment}
import play.api.libs.json.JsValue
import scala.collection.mutable.ArrayBuffer
import models.User

/**
 * Service to manipulate sections
 */
trait SectionService {

  def listSections(): List[Section]
  
  def get(id: UUID): Option[Section]

  def addTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def removeTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def findByFileId(fileId: UUID): List[Section]

  def findByTag(tag: String, user: Option[User]): List[Section]

  def removeAllTags(id: UUID)

  def comment(id: UUID, comment: Comment)

  def removeSection(s: Section)

  def insert(json: JsValue): String

  def setDescription(id: UUID, descr: String)

  /**
   * Return a list of tags and counts found in sections
   */
  def getTags(user: Option[User]): Map[String, Long]

  /**
   * Update thumbnail used to represent this section.
   */
  def updateThumbnail(sectionId: UUID, thumbnailId: UUID)

  /**
   * Get the list of spaces that the section (section --> file --> dataset(s) --> [collection(s)] --> space(s)) belongs to
   */
  def getParentSpaces(querySectionId: UUID): ArrayBuffer[UUID]
}