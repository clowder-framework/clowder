package models
import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsResult

/**
 *   Versus Extraction of Descriptors with an adapter, an extractor and a measure
 *   @author Smruti Padhy
 */
object VersusExtraction {
  
  case class VersusExtraction(val extraction_id: String, val adapter_name:String, val extractor_name:String, val descriptor:String )
  
  
   implicit object VersusExtraction extends Reads[VersusExtraction] {
    def reads(json: JsValue) ={
     
      val ex_id:String = (json \"extractor_id").as[String]
      val a_name:String=(json \"adapter_name").as[String]
      val ex_name:String=(json \"extractor_name").as[String]
      val des:String=(json \"descriptor").as[String]
      
      JsSuccess(VersusExtraction(ex_id,a_name,ex_name,des))
         
    }

    }
  
  

}