/*
 * Used to pass information about an index between Medici and Versus.
 * Used by VersusPlugin.
 */

package models
import play.api.libs.json._
import play.api.libs.json.Reads
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsResult

case class VersusIndex( 
      val id :String, 
      val MIMEtype :String, 
      val extractorID:String, 
      val measureID:String, 
      val indexerType:String
/*      val indexName:Option[String],
      val indexType:Option[String]*/
  )  
  
object VersusIndex {  
   implicit object Index5 extends Writes[VersusIndex] {
    	  	
	def writes(index: VersusIndex) = Json.obj(
    "id" -> index.id,
    "mimetype" -> index.MIMEtype,
    "extr"->index.extractorID,
    "measure"->index.measureID,
    "typeOfIndexer"->index.indexerType
  )
   }
       
  implicit object Index extends Reads[VersusIndex] {    
	  	//implicit val format: Format[VersusIndex] = Json.format[VersusIndex]	 	  	
	def reads(json: JsValue) ={     
      val maybeID:String = (json \"indexID").as[String]
      val maybeMimeType:String=(json\"MIMEtype").as[String]
      val exType:String=(json\"Extractor").as[String]
      val meType:String=(json\"Measure").as[String]
      val indxrType:String=(json\"Indexer").as[String]
     // val maybeName = (json\"indexName").as[Option[String]]
      //val maybeIndexType = (json\"indexType").as[Option[String]]
      JsSuccess(VersusIndex(maybeID,maybeMimeType,exType,meType,indxrType/*, maybeName, maybeIndexType*/) )         
    }
   }
}
