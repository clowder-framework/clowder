package models

import java.util.Date
import play.api.Play.current
import java.util.ArrayList
import play.api.libs.concurrent
import services.RabbitmqPlugin
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsObject
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import javax.inject.Inject
import services.ExtractorService
import services.DI
import scala.concurrent.Future
import services.ExtractionRequestsService
import java.net.InetAddress
import play.api.libs.ws.Response
/**
 * @author Smruti Padhy 
 * 
 *  DTS extractions information 
 * 
 */

object ExtractionInfoSetUp {
val extractors: ExtractorService =  DI.injector.getInstance(classOf[ExtractorService])
val dtsrequests:ExtractionRequestsService=DI.injector.getInstance(classOf[ExtractionRequestsService])

/*
 * Updates DTS extraction request
 * 
 */
def updateDTSRequests(file_id:UUID,extractor_id:String)={
 
  dtsrequests.updateRequest(file_id,extractor_id)
}

  /**
   * Updates Extractors information:
   * IPs of servers on which extractors are running
   * Currently running extractors' names
   * Input types supported by currently running extractors
   */
  def updateExtractorsInfo() = {
    Logger.debug("updateExtractorsInfo[invoked]")
    val updateStatus = current.plugin[RabbitmqPlugin] match {
      case Some(plugin) => {
        val configuration = play.api.Play.configuration
        val exchange = configuration.getString("medici2.rabbitmq.exchange").getOrElse("medici")
        if (exchange != "") {
          Logger.debug("Exchange is not an empty string: " + exchange)
          updateAndGetStatus(plugin, exchange)
        } else {
          Future(Future("DONE"))
        }
      } //end of case match
      case None => {
        Future(Future("DONE"))
      }
    } //end of match
    updateStatus
  }

  /**
   * Obtains the queues' details attached to an exchange
   * updates the currently running extractors list, ips of the extractors, supported input types, number of extractors instances running
   */
  def updateAndGetStatus(plugin: services.RabbitmqPlugin, exchange: String) = {
    var qDetailsFuture = getQDetailsFutures(plugin, exchange) /* Obtains queues's details as Futures of the List of responses*/
    var status = for {
      qDetailsFutureResponses <- qDetailsFuture
      qDetailsResponses <- qDetailsFutureResponses
    } yield {
      var exDetails = List[ExtractorDetail]()
      var finalQList = updateInfoAndGetQueuesList(plugin, qDetailsResponses) /* updates the extractor details and ips list and obtains the list of currently running extractors*/
      Logger.debug("finalQueue List : " + finalQList)
      extractors.insertExtractorNames(finalQList)
      var ListOfFuturesRoutingKeys = finalQList.map {
        qn => getAllRoutingKeysForQueue(plugin, qn, exchange)
      } //end of qlist map
      var updateInputTypeStatus = for {
        routingKeysLists <- scala.concurrent.Future.sequence(ListOfFuturesRoutingKeys)
      } yield {
        var inputTypes = List[String]()
        val routingKeys = routingKeysLists.flatten
        for (input <- routingKeys) {
          var typearr = input.split("\\.")
          if (!inputTypes.contains(typearr(2)))
            inputTypes = typearr(2) :: inputTypes
        }
        Logger.debug("inputTypes: " + inputTypes)
        extractors.insertInputTypes(inputTypes)
        "DONE"
      } //
      updateInputTypeStatus
    } //end of outer yield qDetails Future
    status
  }//end of updateAndGetStatus method
   
  /**
   *  Obtains the queues' names attached to an exchange where source is the exchange and destination is the queue
   */ 
  def getQDetailsFutures(plugin: services.RabbitmqPlugin, exchange: String) = {
    for {
      qNamesResponse <- plugin.getQueuesNamesForAnExchange(exchange)
    } yield {
      var qNameRKList = List[(String, String)]()
      Logger.trace("qNamesResponse: " + qNamesResponse.json)
      val qNamesJsObjectList = qNamesResponse.json.as[List[JsObject]]
      qNamesJsObjectList.map {
        qNameJsObject =>
          Logger.debug("destination Type: " + (qNameJsObject \ "destination_type").as[String])
          if ((qNameJsObject \ "destination_type").as[String].equals("queue")) {
            qNameRKList = ((qNameJsObject \ "destination").as[String], (qNameJsObject \ "routing_key").as[String]) :: qNameRKList
          }
      }
      Logger.debug("qNameRKList: " + qNameRKList)
      var qdetailsListFuture = for {
        (qname, rk) <- qNameRKList
      } yield {
        plugin.getQueueDetails(qname) // get the complete queue details and its consumer details
      }
      var qdetailsFutureList = scala.concurrent.Future.sequence(qdetailsListFuture)
      qdetailsFutureList
    } //end of yield qNamesResponse
  }
 
  /**
   * updates : extractors details 
   *           currently running extractors list
   *           servers IPs where extractors are running
   */
  def updateInfoAndGetQueuesList(plugin: services.RabbitmqPlugin, qDetailsResponses: List[Response]) = {
    var exDetails = List[ExtractorDetail]()
    var qlistResult = List[String]()
    for (qDetailsResponse <- qDetailsResponses) {
      var qDetailJsObject = qDetailsResponse.json.as[JsObject]
      var numConsumers = qDetailJsObject.\("consumers").as[Int]
      var qname = (qDetailJsObject \ "name").as[String]
      if (numConsumers != 0) {
        val qConsumerDetails = qDetailJsObject.\("consumer_details").as[List[JsObject]]
        var ipsList = List[String]()
        var qlist = List[String]()
        for (qcd <- qConsumerDetails) {
          var peerHost = qcd.\("channel_details").\("peer_host").as[String]
          var hostIP = InetAddress.getLocalHost().getHostAddress()
          Logger.debug("Peer Host: " + peerHost + " hostIP: " + hostIP)
          if (peerHost.contains("::1") || peerHost.contains("127.0.0.1")) {
            Logger.debug("LocalHost: The Extractor is running local to Rabbitmq Server")
            if (!ipsList.contains(hostIP)) {
              ipsList = hostIP :: ipsList
            }
          } else {
            if (!ipsList.contains(peerHost)) {
              ipsList = peerHost :: ipsList
            }
            hostIP = peerHost
          }
          var ed = exDetails.find(l => l.ip == hostIP && l.name == qname)
          ed match {
            case Some(x) =>
              x.count = x.count + 1
            case None =>
              {
                Logger.trace("Before Append : exdetails : " + exDetails)
                exDetails = new ExtractorDetail(hostIP, qname, 1) :: exDetails
                Logger.trace("After Append : exdetails : " + exDetails)
              }
          }

        } // end of for loop
        Logger.debug("exDetails: " + exDetails)
        extractors.insertServerIPs(ipsList)
        extractors.insertExtractorDetail(exDetails)
        qlistResult = qname :: qlistResult
      } //end of if
      else {
        ""
      }
    } //end of for  
    qlistResult
  }

  /**
   * Gets all routing keys for a given queue
   */
  def getAllRoutingKeysForQueue(plugin: services.RabbitmqPlugin, qname: String, exchange: String) = {
    for {
      qbindingResponse <- plugin.getQueueBindings(qname)
    } yield {
      val qbindingjson = qbindingResponse.json
      val qbindingList = qbindingjson.as[List[JsObject]]
      var routingKeysList = List[String]()
      for (qbinding <- qbindingList) {
        Logger.trace("queue name: " + qname + "   Routing Key: " + (qbinding \ "routing_key").toString())
        if ((qbinding.\("source").as[String]) == exchange) {
          routingKeysList = (qbinding \ "routing_key").as[String] :: routingKeysList
        }
      }
      routingKeysList
    }
  }
  
  /**
   * Get all exchanges for a given virtual host 
   * TODO : It will be used for multiple exchanges attached to a virtual host in Future
   */
 def getExchangesFutureList(plugin: services.RabbitmqPlugin): Future[List[String]] = {
    for {
      exResponse <- plugin.getExchanges
    } yield {
      val exjsonlist = exResponse.json.as[List[JsObject]]
      var exlist = List[String]()
      exjsonlist.map {
        ex =>
          Logger.trace("internal: " +(ex \"internal")+"  name:"+(ex \"name").as[String])
          if ((ex \ "internal").as[Boolean] == false) {
            var name = (ex \ "name").as[String]
            exlist = name :: exlist
          } else {}
      }
      Logger.debug("exchanges List: " + exlist.toString)
      exlist
    }
  }
}
