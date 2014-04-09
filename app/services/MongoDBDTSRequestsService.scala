package services
import org.bson.types.ObjectId
import java.util.Date
import play.api.Play.current
import com.novus.salat.dao.{ ModelCompanion, SalatDAO }
import com.mongodb.casbah.commons.MongoDBObject
import java.util.ArrayList
import play.api.libs.concurrent
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsObject
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import models._
import com.mongodb.casbah.Imports._
import com.mongodb.WriteConcern
import MongoContext.context
import java.text.SimpleDateFormat

class MongoDBDTSRequestsService extends DTSRequestsService {

 /* def getList(x:com.mongodb.BasicDBList):List[String]={
    var y=Json.parse(com.mongodb.util.JSON.serialize(x))
    
  }*/
  def getDTSRequests(): List[DTSRequests] = {
    var list_requests = List[DTSRequests]()
    val allRequests = DTSRequests.dao.collection.find()

    for (r <- allRequests) {
      var req = DTSRequests.toObject(r)

      list_requests = req :: list_requests
    }
    list_requests
  }

  def insertRequest(serverIP: String, clientIP: String, filename: String, fileid: String, fileType: String, filesize: Long, uploadDate: Date) = {
    DTSRequests.insert(new DTSRequests(serverIP, clientIP, fileid, filename, fileType, filesize, uploadDate, None, None, None))
    // DTSRequests.dao.collection.insert(MongoDBObject("serverIP"->serverIP,"clientIP"->clientIP,"fileid"->fileid,"filename"->filename,"fileType"->fileType,"filesize"->filesize,"uploadDate"->uploadDate,"Extractors"->null,"startTime"->None,"endTime"->None))
  }

 /* def updateRequest(file_id:ObjectId,extractor_id:String) = {
    val requests = getRequests(file_id,extractor_id)
    Logger.debug("-----UPDATING DTS REQUESTS----------")
    Logger.debug( "NO of Requests to be updated : "+requests.length);
    for (r <- requests) {

      var req = Json.parse(com.mongodb.util.JSON.serialize(r))

      var fileid1 = (req \ ("fileid")).toString()
      var fileid = fileid1.substring(1, fileid1.size - 1)

      Logger.debug("File Id for UPDATE MongoDB DTS REQUESTS: " + fileid)

      var extime = Extraction.getExtractionTime(new ObjectId(fileid))

      var sortedTime = extime.sortBy(_.getTime())
      var len = sortedTime.size

      var ex = Extraction.getExtractorList(new ObjectId(fileid.toString))
     
      val elist = ex.keySet.toList
      Logger.debug("----extractorlist:----"+ elist);
      if (len != 0) {
        var update = $set("extractors" -> elist, "startTime" -> sortedTime(0), "endTime" -> sortedTime(len - 1))
        var result = DTSRequests.dao.collection.update(r, update)
      }

    }
  }*/
  
  def updateRequest(file_id:ObjectId,extractor_id:String) = {
    val requests = getRequests(file_id)
    Logger.debug("-----UPDATING DTS REQUESTS----------")
    Logger.debug( "NO of Requests to be updated : "+requests.length);
    for (r <- requests) {

      var req = Json.parse(com.mongodb.util.JSON.serialize(r))

      var fileid1 = (req \ ("fileid")).toString()
      var fileid = fileid1.substring(1, fileid1.size - 1)

      Logger.debug("File Id for UPDATE MongoDB DTS REQUESTS: " + fileid)

      var extime = Extraction.getExtractionTime(new ObjectId(fileid))
     
      var sortedTime = extime.sortBy(_.getTime())
      var len = sortedTime.size

      var ex = Extraction.getExtractorList(new ObjectId(fileid.toString))
     
      val elist = ex.keySet.toList
      Logger.debug("----extractorlist:----"+ elist);
      if (len != 0) {
        var update = $set("extractors" -> elist, "startTime" -> sortedTime(0), "endTime" -> sortedTime(len - 1))
        var result = DTSRequests.dao.collection.update(r, update)
       // var result = DTSRequests
      }

    }
  }

  
  def getRequests(file_id:ObjectId) = {
    Logger.debug("GET REQUESTS---- fileID"+ file_id.toString)
    //val query = MongoDBObject("endTime" -> None)
    val id=file_id.toString
    val query = MongoDBObject("fileid"->id)
    var requests1=DTSRequests.find(query)
    Logger.debug("REQUEST Length: "+ requests1.length)
    
    var requests = DTSRequests.dao.collection.find(query)
    //.find(query)
    requests
  }

}

object DTSRequests extends ModelCompanion[DTSRequests, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[DTSRequests, ObjectId](collection = x.collection("dtsrequests")) {}
  }

}  
