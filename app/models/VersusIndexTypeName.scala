package models

import play.api.libs.json._
import play.api.libs.json.Reads
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsResult

import play.api.libs.json.Json

//names of the variables here MUST be the same as in versus IndexResource.listJSON, where the Json is created.
case class VersusIndexTypeName(
   indexID: String, 
   MIMEtype:String,  
   Extractor: String, 
   Measure:String, 
   Indexer:String, 
   indexName: Option[String], 
   indexType:Option[String]
   )

object VersusIndexTypeName {
    implicit val format: Format[VersusIndexTypeName] = Json.format[VersusIndexTypeName]

    def addName(index: VersusIndexTypeName, indName:String): VersusIndexTypeName = {
        index.copy(indexName = Some(indName))
    }
    
    def addType(index: VersusIndexTypeName, indType:String): VersusIndexTypeName = {
        index.copy(indexType = Some(indType))
    }
    
    def addTypeAndName(index: VersusIndexTypeName, indType:String, indName:String): VersusIndexTypeName = {
        index.copy(indexType = Some(indType), indexName = Some(indName))
    }
}