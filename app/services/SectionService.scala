package services

import models.Section

/**
 * Created by lmarini on 2/17/14.
 */
trait SectionService {

  def get(id: String): Option[Section]

  def addTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def removeTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String])
}