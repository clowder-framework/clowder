package models
import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsResult

object DescriptorList {
  
  case class DescriptorList(val extraction_id: String, val adapter_name:String, val extractor_name:String, val descriptor:String )
  
  
   implicit object DescriptorList extends Reads[DescriptorList] {
    def reads(json: JsValue) ={
     
      val ex_id:String = (json \"extractor_id").as[String]
      val a_name:String=(json \"adapter_name").as[String]
      val ex_name:String=(json \"extractor_name").as[String]
      val des:String=(json \"descriptor").as[String]
      
      JsSuccess(DescriptorList(ex_id,a_name,ex_name,des))
         
    }

    }
  
  

}