package services.mongodb

import java.util.Date

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import models.{Extraction, ExtractionGroup, ResourceRef, UUID, WebPageResource}
import org.bson.types.ObjectId
import play.api.Logger
import salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.MongoContext.context
import services.{DI, ExtractionService}

/**
 * Use MongoDB to store extractions
 */
class MongoDBExtractionService extends ExtractionService {

  def findIfBeingProcessed(fileId: UUID): Boolean = {
    val allOfFile = Extraction.find(MongoDBObject("file_id" -> new ObjectId(fileId.stringify))).toList
    val extractorsArray: collection.mutable.Map[String, String] = collection.mutable.Map()
    for (currentExtraction <- allOfFile) {
      extractorsArray(currentExtraction.extractor_id) = currentExtraction.status
    }
    return extractorsArray.values.exists(statusString => !(statusString == "DONE" || statusString.contains("StatusMessage.error")))
  }

  def findAll(max: Int): List[Extraction] = {
    Extraction.findAll().limit(max).toList
  }

  def findById(resource: ResourceRef): List[Extraction] = {
    Extraction.find(MongoDBObject("file_id" -> new ObjectId(resource.id.stringify))).toList
  }

  def insert(extraction: Extraction): Option[ObjectId] = {
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

  // Return a mapping of ExtractorName -> (FirstMsgTime, LatestMsgTime, LatestMsg, ListOfAllMessages)
  def groupByType(extraction_list: List[Extraction]): Map[String, ExtractionGroup] = {
    var groupings = Map[String, ExtractionGroup]()

    for (e <- extraction_list) {
      if (groupings.contains(e.extractor_id)) {
        // Update entry in the Map
        val format = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy")
        var grp_start = format.parse(groupings(e.extractor_id).firstMsgTime)
        var grp_end = format.parse(groupings(e.extractor_id).latestMsgTime)
        var grp_endmsg = groupings(e.extractor_id).latestMsg
        var grp_list = groupings(e.extractor_id).allMsgs

        // Use start time for time groupings as backup because end is frequently empty
        e.start match {
          case Some(s) => {
            if (grp_start == "N/A" || s.before(grp_start))
              grp_start = s
            else if (grp_end == "N/A" || s.after(grp_end)) {
              grp_end = s
              grp_endmsg = e.status
            }
            else if (s == grp_end) {
              // Update message chronologically even if timestamp matches
              grp_endmsg = e.status
            }
          }
          case None => {}
        }
        e.end match {
          case Some(n) => {
            if (grp_end == "N/A" || n.after(grp_end)) {
              Logger.info("updating latest msg: "+e.status)
              grp_end = n
              grp_endmsg = e.status
            }
            else if (n == grp_end) {
              // Update message chronologically even if timestamp matches
              grp_endmsg = e.status
            }
          }
          case None => {}
        }
        groupings = groupings + (e.extractor_id -> ExtractionGroup(grp_start.toString, grp_end.toString, grp_endmsg, grp_list :+ e))

      } else {
        // Create new entry in the Map
        val start = e.start match {
          case Some(s) => s.toString
          case None => "N/A"
        }
        val end = e.end match {
          case Some(e) => e.toString
          case None => start
        }
        groupings = groupings + (e.extractor_id -> ExtractionGroup(start, end, e.status, List(e)))
      }

    }

    groupings
  }
}

object Extraction extends ModelCompanion[Extraction, ObjectId] {
  val COLLECTION = "extractions"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[Extraction, ObjectId](collection = mongos.collection(COLLECTION)) {}
}

object WebPageResource extends ModelCompanion[WebPageResource,ObjectId]{
  val COLLECTION = "webpage.resources"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[WebPageResource, ObjectId](collection = mongos.collection(COLLECTION)) {}
}

