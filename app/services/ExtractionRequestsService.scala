package services
import java.util.Date
import models._
/**
 * Tracks extractions requests
 *
 */
trait ExtractionRequestsService {

  def insertRequest(serverIP:String,clientIP:String,filename:String,fileid:UUID,fileType:String,filesize:Long, uploadDate:Date)

  def updateRequest(file_id:UUID,extractor_id:String)

  def getDTSRequests():List[ExtractionRequests]
}
