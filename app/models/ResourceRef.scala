package models

import play.api.libs.json.Json

case class ResourceRef(resourceType: Symbol, id: UUID)

object ResourceRef {
  implicit val ResourceRefFormat = Json.format[ResourceRef]

  val dataset = 'dataset
  val file = 'file
  val collection = 'collection
  val user = 'user
}
