package models
import org.bson.types.ObjectId
import java.util.Date
import play.api.Play.current
import services.MongoSalatPlugin
import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
import MongoContext.context
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

case class ExtractorNames (
name:String=""    
)

/*object ExtractorNames extends ModelCompanion[ExtractorNames, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[ExtractorNames, ObjectId](collection = x.collection("extractor.names")) {}
  }
  
  
 def getExtractorNames()={
   var list_queue=List[String]()

  Logger.debug("------getExtractorNames-----")
  var allDocs=dao.collection.find()
  for(doc <- allDocs) {
          var doc1=com.mongodb.util.JSON.serialize(doc)
          var doc2=  Json.parse(doc1)
            println( doc2.\("name").toString)
            list_queue=doc2.\("name").toString :: list_queue
     }
  Logger.debug("----Extractor Name List----") 
  Logger.debug(list_queue.toString)
  list_queue
 }
}*/