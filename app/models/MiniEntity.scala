package models

import play.api.libs.json._

/**
 * Class to contain a subset of Entity data for fast loading.
 *
 * @author Yibo Guo
 */
case class MiniEntity(
  id: UUID,
  name: String,
  objectType: String)

object MiniEntity {
  implicit val miniEntityFormat = Json.format[MiniEntity]
}
