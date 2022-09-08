package models

import java.util.Date

import play.api.libs.json._


/**
 * Add and remove tags
 *
 */
case class Tag(
  id: UUID = UUID.generate,
  name: String,
  userId: Option[String],
  extractor_id: Option[String],
  created: Date) {
   def to_json() : JsValue = {
      return Json.toJson(name)
   }
  }
