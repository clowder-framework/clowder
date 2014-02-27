package services

import models.{Section, Comment}
import play.api.libs.json.JsValue

/**
 * Created by lmarini on 2/17/14.
 */
trait SectionService {

  def get(id: String): Option[Section]

  def addTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def removeTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def findByFileId(fileId: String): List[Section]

  def findByTag(tag: String): List[Section]

  def removeAllTags(id: String)

  def comment(id: String, comment: Comment)

  def removeSection(s: Section)

  def insert(json: JsValue): String
}