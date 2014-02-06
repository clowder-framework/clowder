package models

import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.libs.json.JsSuccess

object Result {
  case class Result( val docID :String, val proximity :Double)
 
  
  
   implicit object Result extends Reads[Result] {
    def reads(json: JsValue) ={
     
      val maybedocID:String = (json \"docID").as[String]
      val maybeProx:Double=(json\"proximity").as[Double]
      JsSuccess(Result(maybedocID,maybeProx) )
         
    }

    }
}