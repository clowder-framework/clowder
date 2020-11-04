package services.mongodb

import java.text.SimpleDateFormat

import services.ExtractionService
import models.{UUID, Extraction, ExtractionGroup, ResourceRef}
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

  def get(msgId: UUID): Option[Extraction] = {
    Extraction.findOne(MongoDBObject("id" -> new ObjectId(msgId.stringify)))
  }

  def findById(resource: ResourceRef): List[Extraction] = {
    Extraction.find(MongoDBObject("file_id" -> new ObjectId(resource.id.stringify))).toList
  }

  def findByExtractorIDBefore(extractorID: String, status: String, date: String, limit: Int): List[Extraction] = {
    val order = MongoDBObject("start"-> -1 )
    val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(date)
    Extraction.find($and("extractor_id" $eq extractorID, "status" $eq status, "start" $gte sinceDate)).sort(order).limit(limit).toList
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
	    extractorsTimeArray = currentExtraction.start :: extractorsTimeArray
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
    // map by extractor-type, then by job_id
    var groupings = Map[String, ExtractionGroup]()

    for (e <- extraction_list) {
      var job_id = e.job_id.getOrElse(UUID(""))
      // Update timestamp and groupings
      if (groupings.contains(e.extractor_id)) {
        // Update entry in the Map
        val format = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy")
        var grp_start = format.parse(groupings(e.extractor_id).firstMsgTime)
        var grp_end = format.parse(groupings(e.extractor_id).latestMsgTime)
        var grp_endmsg = groupings(e.extractor_id).latestMsg

        // For each job_id, keep track of all extraction events for that job
        val init_grp_map = groupings(e.extractor_id).allMsgs
        val grp_map = if(init_grp_map.contains(job_id)) {
          init_grp_map + (job_id -> (List(e) ++ init_grp_map(job_id)))
        } else {
          init_grp_map + (job_id -> List(e))
        }

        // Use start time for time groupings as backup because end is frequently empty
        val s = e.start
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
        e.end match {
          case Some(n) => {
            if (grp_end == "N/A" || n.after(grp_end)) {
              Logger.info("updating latest msg: " + e.status)
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
        groupings = groupings + (e.extractor_id -> ExtractionGroup(grp_start.toString, grp_end.toString, grp_endmsg, grp_map))

      } else {
        // Create new entry in the Map
        val start = e.start.toString
        val end = e.end match {
          case Some(e) => e.toString
          case None => start
        }
        groupings = groupings + (e.extractor_id -> ExtractionGroup(start, end, e.status, Map(job_id -> List(e))))
      }

    }
    groupings.withDefaultValue(ExtractionGroup("N / A", "N / A", "N / A", Map()))
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

