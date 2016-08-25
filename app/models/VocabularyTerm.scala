package models

import java.util.Date
import play.api.libs.json.{Json, JsValue, Writes}
import securesocial.core.Identity

case class VocabularyTerm(
  id : UUID = UUID.generate(),
  author : Option[Identity],
  created : Date = new Date(),
  key : String,
  units : String = "",
  default_value : String = "",
  description : String = "",
  spaces : List[UUID] = List.empty
  )


object VocabularyTerm {
  implicit val vocabularyTermWrites = new Writes[VocabularyTerm] {
    def writes(vocabularyTerm : VocabularyTerm) : JsValue = {
      Json.obj("vocab_term_id" -> vocabularyTerm.id.toString,"key"->vocabularyTerm.key,"default_value"->vocabularyTerm.default_value,"units"->vocabularyTerm.units,"description"->vocabularyTerm.description)
    }
  }
}



