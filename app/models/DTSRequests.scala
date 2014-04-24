package models
import java.util.Date
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
/**
 * DTS Requests information
 * @author Smruti Padhy
 */

case class DTSRequests(
    serverIP:String,
    clientIP:String,
    fileid:String,
    filename:String,
    fileType:String,
    filesize:Long,
    uploadDate:Date,
    extractors:Option[List[String]],
    startTime:Option[Date],
    endTime:Option[Date]
    )
    
 
