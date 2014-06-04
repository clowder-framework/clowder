package models
import java.util.Date

/**
 * DTS Requests information
 * @author Smruti Padhy
 */

case class DTSRequests(
    serverIP:String,
    clientIP:String,
    fileid:UUID,
    filename:String,
    fileType:String,
    filesize:Long,
    uploadDate:Date,
    extractors:Option[List[String]],
    startTime:Option[Date],
    endTime:Option[Date]
    )
    
 
