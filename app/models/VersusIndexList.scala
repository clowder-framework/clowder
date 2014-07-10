package models

import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.libs.json.JsSuccess

/**
 * Keep track of available Versus indexes.
 */
object VersusIndexList {
  
  case class VersusIndexList(val indexID: String, val MIMEtype: String, val ex: String, val me: String, val indxr: String)

  implicit object VersusIndexList extends Reads[VersusIndexList] {
    def reads(json: JsValue) = {
      val maybeID: String = (json \ "indexID").as[String]
      val maybeType: String = (json \ "MIMEtype").as[String]
      val exType: String = (json \ "Extractor").as[String]
      val meType: String = (json \ "Measure").as[String]
      val indxrType: String = (json \ "Indexer").as[String]
      JsSuccess(VersusIndexList(maybeID, maybeType, exType, meType, indxrType))
    }
  }
}
