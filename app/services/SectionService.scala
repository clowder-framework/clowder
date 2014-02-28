package services

import models.{UUID, Section, Comment}
import play.api.libs.json.JsValue

/**
 * Created by lmarini on 2/17/14.
 */
trait SectionService {

  def get(id: UUID): Option[Section]

  def addTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def removeTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def findByFileId(fileId: UUID): List[Section]

  def findByTag(tag: String): List[Section]

  def removeAllTags(id: UUID)

  def comment(id: UUID, comment: Comment)

  def removeSection(s: Section)

  def insert(json: JsValue): String
}