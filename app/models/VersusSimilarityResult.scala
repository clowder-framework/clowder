package models

import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.libs.json.JsSuccess

/**
 * Multimedia search result.
 */
object VersusSimilarityResult {

  case class VersusSimilarityResult(val docID: String, val proximity: Double)

  implicit object VersusSimilarityResult extends Reads[VersusSimilarityResult] {
    def reads(json: JsValue) = {
      val maybedocID: String = (json \ "docID").as[String]
      val maybeProx: Double = (json \ "proximity").as[Double]
      JsSuccess(VersusSimilarityResult(maybedocID, maybeProx))
    }
  }
}