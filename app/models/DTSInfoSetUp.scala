package models

import org.bson.types.ObjectId
import java.util.Date
import play.api.Play.current
import services.MongoSalatPlugin
import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
import MongoContext.context
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
import javax.inject.Inject
import services.ExtractorService
import services.DI
import scala.concurrent.Future
import services.DTSRequestsService
import models.Extraction

object DTSInfoSetUp {
val extractors: ExtractorService =  DI.injector.getInstance(classOf[ExtractorService])
val dtsrequests:DTSRequestsService=DI.injector.getInstance(classOf[DTSRequestsService])

def updateDTSRequests()={
  dtsrequests.updateRequest()
}


 def updateExtractorsInfo()={
  val x=  current.plugin[RabbitmqPlugin] match {
      case Some(plugin) => {
        val configuration = play.api.Play.configuration
        var futureIPs = plugin.getServerIPs() /* Get Channel IPs*/
        var ips = for {
          ipsResponse <- futureIPs /* Convert Future Response to Response*/
        } yield {
          val ipsjson = ipsResponse.json

          val ipsjsonlist = ipsjson.as[List[JsObject]] /*Convert JsValue to List of JsObject(Channel Objects) that enables to traverse*/

          val ulist = ipsjsonlist.map { //for each channel object get the Channel Name that contains IP address
            svr =>

              Logger.debug("Server Name : " + svr \ "name")
              val ipl = (svr \ "name") //Get the Channel name by traversing the JsObject

              // val ipl=xyz.as[JsObject] \"name"

              //  Logger.debug("ipl.toString====" + ipl.toString())

              val ipltoString = ipl.toString //Convert from JsValue to To String

              val ipls = ipltoString.substring(1, ipltoString.length() - 5) // get read of the quotation mark and String (1) in the end of the name

              //Logger.debug("substring ====" + ipls)

              val url = java.net.URLEncoder.encode(ipls, "UTF-8")

              val c = "%20"
              val url1 = url.replaceAll("\\+", c) + "%20(1)"
              Logger.debug("________________________________________________")
              Logger.debug("")

              // Logger.debug("URLencoded====" + url1)
              (ipltoString, url1)
          } //end of map
          ulist
        } //end of first yield

        /*
		                   * Get the channel details
		                   * extract consumer_tags field
		                   * if it is ctag, it denotes consumer, otherwise publisher
		                   * We want consumers, that is server IPs that is running extractor and name of the extractor
		                   */

        var finalResult = for {
          chiplist <- ips /*get the channel IPs as response*/
        } yield { /* start of 2nd yield*/

          //Logger.debug("-------Loop through each Channel IP-------")

          var xylist = chiplist.map {
            url1 =>
              var chdetailFuture = plugin.getChannelInfo(url1._2) /*Get the Channel Details using the encoded url; i.e., url1._2*/

              var dlist = for {
                cdetailResponse <- chdetailFuture /*Get the response for the async call*/
              } yield {
                Logger.debug("---------IP:----- " + url1._1)
                val cdetailjson = cdetailResponse.json

                val cdetail = cdetailjson.as[JsObject] /*convert the json response to JsObject to make it traversable*/
                var consumer_details_List = (cdetail \\ "consumer_details").toList

                //Logger.debug("CONSUMER DETAILS====" + consumer_details_List)

                var consumer_tags = List[String]()
                var queue: Seq[JsValue] = null
                var queuename: Seq[JsValue] = null

                for (ct <- consumer_details_List) {

                  var ctag = ct \\ "consumer_tag"
                  // Logger.debug("cTAG:::::" + ctag)
                  consumer_tags = ctag(0).toString :: consumer_tags

                  queue = ct \\ "queue"
                  queuename = queue(0) \\ "name"
                  Logger.debug(" Queuename: " + queuename(0))
                }

                var flag = false
                for (xt <- consumer_tags) {
                  //var str = xt(0).toString
                  var str = xt
                  var substr = str.substring(1, str.length - 1)
                  //Logger.debug("str=" + substr)
                  if (substr == ("ctag1.0")) {
                    Logger.debug(substr + " :::  CONSUMER")
                    flag = true
                  } else {
                    Logger.debug(substr + " ::: PUBLISHER")
                  }
                } //end of for
                //Logger.debug("-----End of IP--------" + url1._1)

                (url1._1, flag, queuename(0).toString)

              } //end of yield

              dlist
          } //end of for url1<- MAP

          var listSequenced = for {
            resultSequenced <- scala.concurrent.Future.sequence(xylist)
          } yield {
            resultSequenced
          }

          listSequenced

        } //end of 2nd yield   

      var z=  for {
          fin <- finalResult
          finalResponse <- fin
        } yield {

          var kslist = List[String]()
          var qlist=List[String]()
          var subi: String = ""
          var subq: String = ""
          for (i <- finalResponse) { /*(ip,flag,queue name)*/
            if (i._2 == true) { /* extractor running in server is a consumer*/

              subi = i._1.substring(1, i._1.length - 1) /*to get rid of quotation marks in ip address*/
              subq = i._3.substring(1, i._3.length - 1) /*to get rid of quotation marks in queue name,i.e., extractor name*/

              if (subi.contains("[::1]") || subi.contains("127.0.0.1")) {
                Logger.debug("LocalHost: The Extractor is running local to Rabbitmq Server")
                Logger.debug("GET the rabbitmq host name")
                var host = configuration.getString("rabbitmq.host").getOrElse("")
                if (!kslist.contains(host) && !kslist.contains("127.0.0.1"))
                  // kslist =  host+ "-" + subq :: kslist
                 //  kslist = host :: kslist
                  kslist="127.0.0.1" :: kslist

              } else {
                var iparr = subi.split('-')(0).split(':')
                if (!kslist.contains(iparr(0)))
                  kslist = iparr(0) :: kslist
              }
              if(!qlist.contains(subq))
                 qlist=subq::qlist
            }
          }
          extractors.insertServerIPs(kslist)
                
          extractors.insertExtractorNames(qlist)
                    
      var rktypelist=qlist.map{
		             qn=> 
			              var rktype=for{
			                 qbindingResponse<-plugin.getQueueBindings("/", qn)
			                }yield{
			                  var frk=""
			                  val qbindingjson=qbindingResponse.json
			                  val qbindingList=qbindingjson.as[List[JsObject]]
			                  val rkList=qbindingList.map{
			                                qb=> 
			                                  Logger.debug("queue name:"+ qn+ "   Routing Key: "+ (qb\"routing_key").toString())
			                                  (qb\"routing_key").toString()
			                              }//end of map
			                  for(rk<-rkList){
			                       if(rk!=qn){
			                            frk=rk
			                        }
			                  }
			                  frk
			                }
			            
		                rktype
                 }//end of qlist map
          
         var y= for{
            types<-scala.concurrent.Future.sequence(rktypelist)
            }yield{
              var inputTypes=List[String]()
              for(input<-types){
                 var typearr=input.split("\\.")
                 if(!inputTypes.contains(typearr(2)))
                  inputTypes=typearr(2)::inputTypes
              }
              Logger.debug("inputTypes: "+inputTypes)
              extractors.insertInputTypes(inputTypes)
             "DONE"
            }
          
         y //Ok(Json.obj("Servers"->kslist))
         // kslist
        } //end of yield
       
       z 
      
      } //end of case match

      case None => {
       Future(Future("DONE"))
      }
    } //end of match
  x
  }
  
  
}