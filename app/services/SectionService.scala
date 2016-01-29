package services

import models.{UUID, Section, Comment}
import play.api.libs.json.JsValue
import scala.collection.mutable.ArrayBuffer

/**
 * Created by lmarini on 2/17/14.
 */
trait SectionService {

  def listSections(): List[Section]
  
  def get(id: UUID): Option[Section]

  def addTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def removeTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def findByFileId(fileId: UUID): List[Section]

  def findByTag(tag: String): List[Section]

  def removeAllTags(id: UUID)

  def comment(id: UUID, comment: Comment)

  def removeSection(s: Section)

  def insert(json: JsValue): String

  def setDescription(id: UUID, descr: String)

  /**
   * Return a list of tags and counts found in sections
   */
  def getTags(): Map[String, Long]

  /**
   * Update thumbnail used to represent this section.
   */
  def updateThumbnail(sectionId: UUID, thumbnailId: UUID)

  /**
   * Get the list of spaces that the section (section --> file --> dataset(s) --> [collection(s)] --> space(s)) belongs to
   */
  def getParentSpaces(querySectionId: UUID): ArrayBuffer[UUID]
}