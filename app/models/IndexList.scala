package models
import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsResult

object IndexList {




  //case class IndexList( val indexID :String, val MIMEtype :String)
  case class IndexList( val indexID :String, val MIMEtype :String, val ex:String, val me:String, val indxr:String)
  
  
   implicit object IndexList extends Reads[IndexList] {
    def reads(json: JsValue) ={
     
      val maybeID:String = (json \"indexID").as[String]
      val maybeType:String=(json\"MIMEtype").as[String]
      val exType:String=(json\"Extractor").as[String]
      val meType:String=(json\"Measure").as[String]
      val indxrType:String=(json\"Indexer").as[String]
      JsSuccess(IndexList(maybeID,maybeType,exType,meType,indxrType) )
         
    }

    }
}
