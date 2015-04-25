package services.mongodb

import services._
import models._
import org.bson.types.ObjectId
import java.util.Date
import play.api.Play.current
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.commons.MongoDBObject
import java.util.ArrayList
import play.api.libs.concurrent
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsObject
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import com.mongodb.casbah.Imports._
import javax.inject.{Singleton, Inject}
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
import com.mongodb.util.JSON
import scala.Some
import scala.util.parsing.json.JSONArray
import services.mongodb.MongoContext.context
import services.mongodb.MongoSalatPlugin
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.JsNumber

@Singleton
class MongoDBExtractorService extends ExtractorService {

    
  def getExtractorServerIPList() = {
    var listServersIPs = List[String]()
    Logger.debug("[mongodbextractorservice]--getServerIPList()")
    var allDocs = ExtractorServer.dao.collection.find()
    for (doc <- allDocs) {
      var doc1 = com.mongodb.util.JSON.serialize(doc)
      var doc2 = Json.parse(doc1)
      var doc3=doc2.\("server").asOpt[String]
      doc3.map{
        t=>listServersIPs = t :: listServersIPs
      }.getOrElse{}
    }
    Logger.debug("[mongodbextractorservice]--Servers List-")
    Logger.debug(listServersIPs.toString)
    listServersIPs
  }

   
  def insertServerIPs(iplist: List[String]) = {
    for (sip <- iplist) {
      ExtractorServer.insert(new ExtractorServer(sip), WriteConcern.Safe)
      Logger.debug("[mongodbextractorservice]--extractor.servers: document inserted : " + sip)
    }
  }

  def getExtractorNames() = {
    var list_queue = List[String]()

    Logger.debug("[MongoDBExtractorService]- getExtractorNames")
    var allDocs = ExtractorNames.dao.collection.find()
    for (doc <- allDocs) {
      var doc1 = com.mongodb.util.JSON.serialize(doc)
      var doc2 = Json.parse(doc1)
      var doc3=doc2.\("name").asOpt[String]
      doc3.map{
        t=>list_queue = t :: list_queue
      }.getOrElse{}
      
    }
    Logger.debug("[MongoDBExtractorService]- Extractor Name List-")
    Logger.debug(list_queue.toString)
    list_queue
  }

 
  def insertExtractorNames(exlist: List[String]) = {
    for (qn <- exlist) {
      Logger.debug("[mongodbextractorservice]--extractor.names: document inserted: " + qn)
      ExtractorNames.insert(new ExtractorNames(qn),WriteConcern.Safe)
    }
  }
   
  def dropAllExtractorStatusCollection()={
    val exNames = ExtractorNames.dao.collection
    val exDetails = ExtractorDetailDAO.dao.collection
    val exServers = ExtractorServer.dao.collection
    val exInputTypes = ExtractorInputType.dao.collection
    exNames.drop()
    exDetails.drop()
    exServers.drop()
    exInputTypes.drop()
    Logger.debug("Collections Dropped: extractors.names,extractors.details,extractor.servers,exractor.inputtypes")
  } 
//------------------------Temporary fix--------------------------------
 /**
 * 
 *This is a temporary fix for keeping track of extractor servers IPs, names and the extractors' counts 
 * This will be omitted in future version 
 * 
 * * 
 */
  def insertExtractorDetail(exDetails: List[ExtractorDetail]) = {
    for (qn <- exDetails) {
      Logger.debug("[mongodbextractorservice]--extractor.details: document inserted: " + qn)
      ExtractorDetailDAO.insert(qn,WriteConcern.Safe)
    }
  }
 
 def getExtractorDetail():  Option[JsValue]={
    var edArray = new JsArray()
    Logger.debug("[MongoDBExtractorService]- getExtractorDetails")
    var allDocs = ExtractorDetailDAO.findAll
    for (doc <- allDocs) {
      edArray = edArray :+ JsObject(Seq("ip"->JsString(doc.ip),"extractor_name"->JsString(doc.name),"count"->JsNumber(doc.count)))
    }
    Logger.debug("[MongoDBExtractorService]- Extractor Detail List-")
    Logger.debug(edArray.toString)
    Some(edArray)
 }
 //------------------------------------------End of Temporary fix-------------  
   
  def getExtractorInputTypes() = {
    var list_inputs = List[String]()

    Logger.debug("[mongodbextractorservice]--getExtractorInputTypes()")
    var allDocs = ExtractorInputType.dao.collection.find()
    for (doc <- allDocs) {
      var doc1 = com.mongodb.util.JSON.serialize(doc)
      var doc2 = Json.parse(doc1)
      var doc3=doc2.\("inputType").asOpt[String]
      doc3.map{
        t=>list_inputs = t :: list_inputs
      }.getOrElse{}
    }
    Logger.debug("[mongodbextractorservice]--Extractor Input List")
    Logger.debug(list_inputs.toString)
    list_inputs
  }

  def insertInputTypes(inputTypes: List[String]) = {
    for (ipt <- inputTypes) {
      ExtractorInputType.insert(new ExtractorInputType(ipt),WriteConcern.Safe)
      Logger.debug("[mongodbextractorservice]--extractor.inputtypes: document inserted: " + ipt)
    }
  }

} //end of class

object ExtractorServer extends ModelCompanion[ExtractorServer, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ExtractorServer, ObjectId](collection = x.collection("extractor.servers")) {}
  }
}

object ExtractorNames extends ModelCompanion[ExtractorNames, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ExtractorNames, ObjectId](collection = x.collection("extractor.names")) {}
  }
}

object ExtractorInputType extends ModelCompanion[ExtractorInputType, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ExtractorInputType, ObjectId](collection = x.collection("extractor.inputtypes")) {}
  }
} 

/**
 * Temporary Fix: Creating a mongodb collection for keeping track of extractors details
 */
 object ExtractorDetailDAO extends ModelCompanion[ExtractorDetail, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ExtractorDetail, ObjectId](collection = x.collection("extractor.details")) {}
  } 
 }

