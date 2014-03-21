package controllers
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

class DTSInfo @Inject() (extractors: ExtractorService, dtsrequests: DTSRequestsService) extends SecuredController {

  /**
   * extractor server ip
   */

  def getExtractorServersIP() = SecuredAction(authorization = WithPermission(Permission.Public)) { implicit request =>
    Async {
      for {
        x <- DTSInfoSetUp.updateExtractorsInfo()
        status <- x
      } yield {

        Logger.debug("Update Status:" + status)
        val list_servers = extractors.getExtractorServerIPList()
        var jarr = new JsArray()
        var list_servers1=List[String]()
        list_servers.map {
          ls =>
            Logger.debug("Server Name:  " + ls.substring(1, ls.size-1))
            jarr = jarr :+ (Json.parse(ls))
            list_servers1=ls.substring(1, ls.size-1)::list_servers1
            }
        Logger.debug("JSARRAY----" + jarr.toString)
       //Ok(Json.obj("Servers" -> jarr))
        Ok(views.html.dtsserverip(list_servers1,list_servers1.size))
      }
    }
  }

  def getExtractorNames() = SecuredAction(authorization = WithPermission(Permission.Public)) { implicit request =>

    val list_names = extractors.getExtractorNames()
    var jarr = new JsArray()
    var list_names1=List[String]()
    list_names.map {
      ls =>
        Logger.debug("Extractor Name:  " + ls)
        jarr = jarr :+ (Json.parse(ls))
        list_names1=ls.substring(1, ls.size-1)::list_names1

    }
    Logger.debug("JSARRAY----" + jarr.toString)
    //Ok(Json.obj("Extractors" -> jarr))
    Ok(views.html.dtsextractors(list_names1,list_names1.size))

  }

  def getExtractorInputTypes() = SecuredAction(authorization = WithPermission(Permission.Public)) { implicit request =>

    val list_inputtypes = extractors.getExtractorInputTypes()
    var jarr = new JsArray()
    var list_inputtypes1=List[String]()
    list_inputtypes.map {
      ls =>
        Logger.debug("Extractor Input Type:  " + ls)
        jarr = jarr :+ (Json.parse(ls))
        list_inputtypes1=ls.substring(1, ls.size-1)::list_inputtypes1

    }
    Logger.debug("JSARRAY----" + jarr.toString)
   // Ok(Json.obj("InputTypes" -> jarr))
     Ok(views.html.dtsinputtype(list_inputtypes1,list_inputtypes1.size))

  }
  
  def getDTSRequests() = SecuredAction(authorization = WithPermission(Permission.Public)) { implicit request =>

    var list_requests = dtsrequests.getDTSRequests()
    var startTime = models.ServerStartTime.startTime
    var currentTime = Calendar.getInstance().getTime()
    Ok(views.html.dtsrequests(list_requests, list_requests.size, startTime, currentTime))
  }

}