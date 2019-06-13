package models

import java.util.Date
import play.api.libs.json.{JsValue, Json, Writes}

// XXX: We cannot name this StandardVocabulary, as that collides
// with MongoDB's built-in object definitions
case class StandardVocab(
  id : UUID = UUID.generate(),
  created : Date = new Date(),
  lastModified : Date = new Date(),
  terms : List[String] = List.empty)

object StandardVocab{
  implicit val vocabWrites = new Writes[StandardVocab] {
    def writes(vocabulary : StandardVocab) : JsValue = {
      Json.obj(
        "id" -> vocabulary.id.toString,
        "created" -> vocabulary.created.toString,
        "lastModified" -> vocabulary.lastModified.toString,
        "url" -> ("/api/standardvocab/" + vocabulary.id.toString + "/terms"),
        "terms" -> vocabulary.terms.toList)
    }
  }
}