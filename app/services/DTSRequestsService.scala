package services
import java.util.Date
import models._

trait DTSRequestsService {
  
  def insertRequest(serverIP:String,clientIP:String,filename:String,fileid:String,fileType:String,filesize:Long, uploadDate:Date)   
  
  def updateRequest()
  
  def getDTSRequests():List[DTSRequests]
}