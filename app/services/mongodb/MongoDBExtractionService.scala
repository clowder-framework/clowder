package services.mongodb

import services.ExtractionService
import models.{UUID, Extraction}
import org.bson.types.ObjectId
import play.api.Play.current
import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
import MongoContext.context
import com.mongodb.casbah.commons.MongoDBObject
import java.util.Date
import play.api.Logger
import models.WebPageResource
import com.mongodb.casbah.Imports._

/**
 * Created by lmarini on 2/21/14.
 */
class MongoDBExtractionService extends ExtractionService {

  def findIfBeingProcessed(fileId: UUID): Boolean = {
    val allOfFile = Extraction.find(MongoDBObject("file_id" -> new ObjectId(fileId.stringify))).toList
    val extractorsArray: collection.mutable.Map[String, String] = collection.mutable.Map()
    for (currentExtraction <- allOfFile) {
      extractorsArray(currentExtraction.extractor_id) = currentExtraction.status
    }
    return extractorsArray.values.exists(_ != "DONE")
  }

  def findAll(): List[Extraction] = {
    Extraction.findAll().toList
  }

  def findByFileId(fileId: UUID): List[Extraction]= {
    Extraction.find(MongoDBObject("file_id" -> new ObjectId(fileId.stringify))).toList
  }

  def insert(extraction: Extraction) {
    Extraction.insert(extraction)
  }
  
  /**
   * Returns list of extractors and their corresponding status for a specified file
   */
  
  def getExtractorList(fileId:UUID):collection.mutable.Map[String,String] = {
    val allOfFile = Extraction.find(MongoDBObject("file_id" -> new ObjectId(fileId.stringify))).toList
	var extractorsArray:collection.mutable.Map[String,String] = collection.mutable.Map()
	for(currentExtraction <- allOfFile){
	  extractorsArray(currentExtraction.extractor_id) = currentExtraction.status
	}
    return extractorsArray
  }

  def getExtractionTime(fileId:UUID):List[Date] ={
  val allOfFile = Extraction.find(MongoDBObject("file_id" -> new ObjectId(fileId.stringify))).toList
	var extractorsTimeArray=List[Date]()
	for(currentExtraction <- allOfFile){
	    extractorsTimeArray = currentExtraction.start.get :: extractorsTimeArray
	}
  return extractorsTimeArray
}

  def save(webpr:WebPageResource):UUID={
    WebPageResource.insert(webpr,WriteConcern.Safe)
    webpr.id
  }
  
  def getWebPageResource(id: UUID): Map[String,String]={
    val wpr=WebPageResource.findOne(MongoDBObject("_id"->new ObjectId(id.stringify)))
    var wprlist= wpr.map{
      e=>Logger.debug("resource id:" + id.toString)
         e.URLs
    }.getOrElse(Map.empty)
    wprlist         
  }
  
}

object Extraction extends ModelCompanion[Extraction, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Extraction, ObjectId](collection = x.collection("extractions")) {}
  }
}

object WebPageResource extends ModelCompanion[WebPageResource,ObjectId]{
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[WebPageResource, ObjectId](collection = x.collection("webpage.resources")) {}
  }
}

