/*
 * Used to pass information about an index between Medici and Versus.
 * Used by VersusPlugin.
 */

package models
import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsResult

object VersusIndex {

  case class VersusIndex( 
      val id :String, 
      val MIMEtype :String, 
      val extractorID:String, 
      val measureID:String, 
      val indexerType:String)  
  
   implicit object Index extends Reads[VersusIndex] {
	def reads(json: JsValue) ={
     
      val maybeID:String = (json \"indexID").as[String]
      val maybeType:String=(json\"MIMEtype").as[String]
      val exType:String=(json\"Extractor").as[String]
      val meType:String=(json\"Measure").as[String]
      val indxrType:String=(json\"Indexer").as[String]
      JsSuccess(VersusIndex(maybeID,maybeType,exType,meType,indxrType) )
         
    }

   }

}

