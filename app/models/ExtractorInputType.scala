package models

import org.bson.types.ObjectId
import java.util.Date
import play.api.Play.current
//import services.MongoSalatPlugin
import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
//import MongoContext.context
import com.mongodb.casbah.commons.MongoDBObject
import java.util.ArrayList
import play.api.libs.concurrent
import services.RabbitmqPlugin
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsObject
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import models.ExtractorNames

case class ExtractorInputType (
inputType:String=""    
)

/*object ExtractorInputType extends ModelCompanion[ExtractorInputType, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[ExtractorInputType, ObjectId](collection = x.collection("extractor.inputtypes")) {}
  }
  
 def getExtractorInputTypes()={
   var list_inputs=List[String]()

  Logger.debug("------getExtractorInputTypes-----")
  var allDocs=dao.collection.find()
  for(doc <- allDocs) {
          var doc1=com.mongodb.util.JSON.serialize(doc)
          var doc2=  Json.parse(doc1)
            println( doc2.\("inputType").toString)
            list_inputs=doc2.\("inputType").toString :: list_inputs
     }
  Logger.debug("----Extractor Input List----") 
  Logger.debug(list_inputs.toString)
  list_inputs
 }
  
 }*/
