package api
import play.api.Logger
import play.api.Play.current
import play.api.mvc._
import services._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.concurrent.Promise
import scala.concurrent.Future
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import java.util.Date
import play.api.libs.json.Json._
import models.Extraction
import api.WithPermission
import api.Permission
import javax.inject.Inject
import play.api.libs.json.JsObject
import play.api.libs.json.JsArray
import services.ExtractorService
import services.DTSRequestsService
import models.DTSInfoSetUp
import play.api.libs.json._
import java.util.Calendar

class DTSInfo @Inject() (extractors: ExtractorService, dtsrequests: DTSRequestsService) extends ApiController {

  /**
   * extractor server ip
   */

  def getExtractorServersIP() = SecuredAction(parse.anyContent,authorization = WithPermission(Permission.Public)) {  request =>
    Async {
      for {
        x <- DTSInfoSetUp.updateExtractorsInfo()
        status <- x
      } yield {

        Logger.debug("Update Status:" + status)
        val list_servers = extractors.getExtractorServerIPList()
        var jarr = new JsArray()
        //var list_servers1=List[String]()
        list_servers.map {
          ls =>
            Logger.debug("Server Name:  " + ls.substring(1, ls.size-1))
            jarr = jarr :+ (Json.parse(ls))
           // list_servers1=ls.substring(1, ls.size-1)::list_servers1
            }
        Logger.debug("JSARRAY----" + jarr.toString)
        Ok(Json.obj("Servers" -> jarr))
        //Ok(views.html.dtsserverip(list_servers1,list_servers1.size))
      }
    }
  }

  def getExtractorNames() = SecuredAction(parse.anyContent,authorization = WithPermission(Permission.Public)) { request =>

    val list_names = extractors.getExtractorNames()
    var jarr = new JsArray()
    var list_names1=List[String]()
    list_names.map {
      ls =>
        Logger.debug("Extractor Name:  " + ls)
        jarr = jarr :+ (Json.parse(ls))
        //list_names1=ls.substring(1, ls.size-1)::list_names1

    }
    Logger.debug("JSARRAY----" + jarr.toString)
    //Ok(Json.obj("Extractors" -> jarr))
    Ok(toJson(Map("Extractors" -> jarr)))
   

  }

  def getExtractorInputTypes() = SecuredAction(parse.anyContent,authorization = WithPermission(Permission.Public)) {  request =>

    val list_inputtypes = extractors.getExtractorInputTypes()
    var jarr = new JsArray()
    //var list_inputtypes1=List[String]()
    list_inputtypes.map {
      ls =>
        Logger.debug("Extractor Input Type:  " + ls)
        jarr = jarr :+ (Json.parse(ls))
        //list_inputtypes1=ls.substring(1, ls.size-1)::list_inputtypes1

    }
    Logger.debug("JSARRAY----" + jarr.toString)
    Ok(Json.obj("InputTypes" -> jarr))
   

  }
  
  def getDTSRequests() = SecuredAction(parse.anyContent,authorization = WithPermission(Permission.Public)) { request =>
    Logger.debug("---GET DTS Requests---")
    var list_requests = dtsrequests.getDTSRequests()
    var startTime = models.ServerStartTime.startTime
    var currentTime = Calendar.getInstance().getTime()
    
    var jarr=new JsArray()
    var jsarrEx=new JsArray()
    
  list_requests.map{
      dtsreq=>
          var extractors1:JsValue=null
          var extractors2:List[String]=null
          var js=Json.arr()
          
           if(dtsreq.extractors!=None)
           { 
             Logger.debug("----Inside dts requests----")
              extractors1=Json.parse(com.mongodb.util.JSON.serialize(dtsreq.extractors.get))
              extractors2=extractors1.as[List[String]]
             //Logger.debug("Extractors1:"+ Json.stringify(extractors1))
             Logger.debug("Extractors2:"+ extractors2)
             extractors2.map{
                  ex=>
                   js=js:+toJson(ex)
                 }
                        
           }else{
             Logger.debug("----Else block")
           }
               
          jarr=jarr:+(Json.obj("clientIP"->dtsreq.clientIP,"fileid"->dtsreq.fileid.stringify,"filename"->dtsreq.filename,"fileType"->dtsreq.fileType,"filesize"->dtsreq.filesize,"uploadDate"->dtsreq.uploadDate,"extractors"->js ,"startTime"->dtsreq.startTime,"endTime"->dtsreq.endTime))
        }
    
    Ok(jarr)
   
  }
  /*convert list of JsObject to JsArray*/
def getJsonArray(list: List[JsObject]): JsArray = {
    list.foldLeft(JsArray())((acc, x) => acc ++ Json.arr(x))
  }

}