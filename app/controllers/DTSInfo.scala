package controllers
import java.io._
import models.FileMD
import play.api.Logger
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.iteratee._
import play.api.mvc._
import services._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.Input.{ El, EOF, Empty }
import com.mongodb.casbah.gridfs.GridFS
import play.api.libs.ws.WS
import scala.concurrent.Future
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import com.mongodb.casbah.commons.MongoDBObject
import models.FileDAO
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import models.Comment
import java.util.Date
import models.File
import models.Dataset
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import play.api.libs.json.Json._
import play.api.libs.ws.WS
import fileutils.FilesUtils
import models.Extraction
import api.WithPermission
import api.Permission
import javax.inject.Inject
import play.api.libs.json.JsObject
import play.api.libs.json.JsArray
class DTSInfo @Inject() (files: FileService, datasets: DatasetService, queries: QueryService) extends SecuredController {

  /**
   * extractor server ip
   */
  def exServerIP() = SecuredAction(authorization = WithPermission(Permission.Public)) { implicit request =>
    Async {
      current.plugin[RabbitmqPlugin] match {

      case Some(plugin) => {
        
          var futureIPs = plugin.getServerIPs() /* Get Channel IPs*/

          for {
            ipsResponse <- futureIPs      /* Convert Future Response to Response*/
          } yield {
            
            val ipsjson = ipsResponse.json
            
            val ipsjsonlist = ipsjson.as[List[JsObject]] /*Convert JsValue to List of JsObject(Channel Objects) that enables to traverse*/

            val iplist = ipsjsonlist.map { //for each channel object get the Channel Name that contains IP address
		                 svr =>
		
		                 Logger.debug("Server Name : " + svr \ "name") 
		                 val ipl = (svr \ "name") //Get the Channel name by traversing the JsObject
		
		                 // val ipl=xyz.as[JsObject] \"name"
		
		                // Logger.debug("ipl.toString====" + ipl.toString())
		
		                 val ipltoString = ipl.toString //Convert from JsValur to To String
		                 
		                 val ipls = ipltoString.substring(1, ipltoString.length() - 5) // get read of the quotation mark and String (1) in the end of the name
		                 
		                 Logger.debug("substring ====" + ipls)
		                 
		                 val url = java.net.URLEncoder.encode(ipls, "UTF-8")
		
		                 val c = "%20"
		                 val url1 = url.replaceAll("\\+", c) + "%20(1)"
		
		                 Logger.debug("URLencoded====" + url1)
		
		                /*
		                   * Get the channel details
		                   * extract consumer_tags field
		                   * if it is ctag, it denotes consumer, otherwise publisher
		                   * We want consumers
		                   */
		                var cdetailFuture = plugin.getChannelInfo(url1)
		
		                var dlist = for {
		                  cdetailResponse <- cdetailFuture
		                } yield {
		                  val cdetailjson = cdetailResponse.json
		                  //Logger.debug("JSON RESPONSE ++++++"+cdetailjson)
		                  val cdetail = cdetailjson.as[JsObject]
		                  var clist = cdetail \\ "consumer_details"
		
		                  //  var csObjSeq= for(ct<-clist) yield ct.as[JsObject]
		
		                  var ctags = for (ct <- clist) yield ct \\ "consumer_tag"
		
		                  /* var ctags=clist.map{
		                            	  		 ch=>
		                            	  		  // Logger.debug( "Consumer_Detail=======" + ch\\"consumer_details")
		                            	  		  // ch \\"consumer_details"
		                            	  			Logger.debug( "Consumer_tags=======" + ch\\"consumer_tag")
		                            	  			ch\\"consumer_tag"
		                               			}
		                              */
		
		                  var flag = false
		                  for (xt <- ctags) {
		                    var str = xt(0).toString
		                    val substr = str.substring(1, str.length - 1)
		                    Logger.debug("str=" + substr)
		                    if (substr == ("ctag1.0")) {
		                      Logger.debug(substr + " It is a consumer")
		                      flag = true
		                    } else {
		                      Logger.debug(substr + " It is a publisher")
		                    }
		                  } //end of for
		                  flag
		                } //end of inner yield
		                //ipl
		                (ipl, dlist)

            } //end of outer map

            Logger.debug("iplist length " + iplist.length)
            // var filteredlist=iplist.filter(p=>p._2==true)
            //  Logger.debug("filteredlist length "+ filteredlist.length)
            // val finaliplist=filteredlist.map{
            val finaliplist = iplist.map {
              fl =>
                Logger.debug("fl._1 === " + fl._1 + "fl._2==" + fl._2)
                val b1 = for {
                  b <- fl._2
                } yield {
                  (fl._1, b)
                }
                b1
            }
            //finaliplist

            var iplist1 = scala.concurrent.Future.sequence(finaliplist)

            val iplist2 = iplist1.filter(p => f(p))

            for {
              j <- iplist2
            } yield {
              val fl = j.map {
                k => k._1
              }
              fl
              Ok(toJson(fl))
            }
            //  var iplist2=iplist1.flatMap(x=>g(x))
            //  iplist2
            //Ok(iplist1.flatMap(x=>g(x)))
            //   Ok(toJson(iplist3))
            // Ok(toJson(finaliplist))
          } //outer yield
        } //end of case match

        case None => {
          Future(Ok("No Plugin"))
        }
      }//end of match
      }//Async end
    }//end of method

  
  def f(p:List[(JsValue,Boolean)]):Boolean={
    var flag=false
    for(x<-p){
      if(x._2==true){
        flag=true
      }
    else
        flag=false
    } 
    flag
  }
  def g(v:List[List[JsObject]]) = {
    Future(getJsonArray((v.flatten(x=>x))))
   }
  
   def getJsonArray(list: List[JsObject]): JsArray = {
    list.foldLeft(JsArray())((acc, x) => acc ++ Json.arr(x))
  }
   
  }