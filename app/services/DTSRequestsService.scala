package services
import java.util.Date
import models._
import org.bson.types.ObjectId

trait DTSRequestsService {
  
  def insertRequest(serverIP:String,clientIP:String,filename:String,fileid:String,fileType:String,filesize:Long, uploadDate:Date)   
  
  def updateRequest(file_id:ObjectId,extractor_id:String)
  
  def getDTSRequests():List[DTSRequests]
}