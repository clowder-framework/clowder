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
  )  
  
object VersusIndex {   
  /**
   * Serializer for VersusIndex type.
   	*/
	implicit object VersusIndexWrites extends Writes[VersusIndex] {
		def writes(index: VersusIndex) = Json.obj(
				"id" -> index.id,
				"mimetype" -> index.MIMEtype,
				"extr"->index.extractorID,
				"measure"->index.measureID,
				"typeOfIndexer"->index.indexerType
				)
	}
             
	/**
	 * Deserializer for VersusIndex type.
	 */
	implicit object VersusIndexReads extends Reads[VersusIndex] {    
			def reads(json: JsValue) ={  
				val maybeID:String = (json \"indexID").as[String]
				val maybeMimeType:String=(json\"MIMEtype").as[String]
				val exType:String=(json\"Extractor").as[String]
				val meType:String=(json\"Measure").as[String]
				val indxrType:String=(json\"Indexer").as[String]
				JsSuccess(VersusIndex(maybeID,maybeMimeType,exType,meType,indxrType))     
			}
	}
