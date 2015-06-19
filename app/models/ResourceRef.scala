package models

import play.api.libs.json._

case class ResourceRef(resourceType: Symbol, id: UUID)

object ResourceRef {

  implicit val symbolFormat: Format[Symbol] = new Format[Symbol] {

    def reads(json: JsValue) = {
      json match {
        case jsString: JsString => JsSuccess(Symbol(jsString.value))
        case other => JsError("Can't parse json path as an Symbol. Json content = " + other.toString())
      }
    }

    def writes(s: Symbol): JsValue = {
      JsString(s.toString)
    }

  }

  implicit val ResourceRefFormat = Json.format[ResourceRef]

  val space = 'space
  val dataset = 'dataset
  val file = 'file
  val collection = 'collection
  val user = 'user
  val comment = 'comment
}
