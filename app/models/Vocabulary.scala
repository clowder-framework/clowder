package models

import java.util.Date

import play.api.libs.json.{Json, JsValue, Writes}
import securesocial.core.Identity

/**
  * Created by todd_n on 2/8/16.
  */
case class Vocabulary (
  id : UUID = UUID.generate(),
  author : Option[Identity],
  created : Date = new Date(),
  name : String = "",
  lastModified : Date = new Date(),
  keys : List[String] = List.empty)                    


object Vocabulary{
  implicit val vocabularyWritew = new Writes[Vocabulary] {
    def writes(vocabulary : Vocabulary) : JsValue = {
      Json.obj("id" -> vocabulary.id.toString, "keys" -> vocabulary.keys.toList)
    }
  }
}
