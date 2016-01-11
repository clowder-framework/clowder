package models

import play.api.libs.json.{Json, JsValue, Writes}
import securesocial.core.Identity
import java.util.Date

/**
  * Created by todd_n on 1/11/16.
  */
case class Template (
  id : UUID = UUID.generate(),
  author : Identity,
  date : Date,
  lastModified : Date = new Date(),
  keys : List[String] = List.empty)

  object Template{
    implicit val templateWrites = new Writes[Template]{
      def writes(template : Template): JsValue ={
        Json.obj("id" -> template.id.toString, "author" -> template.author.toString, "keys"-> template.keys.toList)
      }
    }
}
