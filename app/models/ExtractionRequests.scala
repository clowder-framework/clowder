package models
import java.util.Date

/**
 * DTS Requests information
 *
 */

case class ExtractionRequests(
    serverIP:String,
    clientIP:String,
    fileId:UUID,
    fileName:String,
    fileType:String,
    fileSize:Long,
    uploadDate:Date,
    extractors:Option[List[String]],
    startTime:Option[Date],
    endTime:Option[Date]
    )
    
 
