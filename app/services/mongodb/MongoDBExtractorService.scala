package services

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
//import com.mongodb.WriteConcern
import javax.inject.{Singleton, Inject}
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
//import com.mongodb.casbah.WriteConcern
import com.mongodb.util.JSON
import jsonutils.JsonUtil
import scala.Some
import scala.util.parsing.json.JSONArray
import services.mongodb.MongoContext.context
import services.mongodb.MongoSalatPlugin
//import com.mongodb.casbah.Imports.WriteConcern

@Singleton
class MongoDBExtractorService extends ExtractorService {

    
  def getExtractorServerIPList() = {
    var list_servers = List[String]()
    Logger.debug("------getServerIPList-----")
    var allDocs = ExtractorServer.dao.collection.find()
    for (doc <- allDocs) {
      var doc1 = com.mongodb.util.JSON.serialize(doc)
      var doc2 = Json.parse(doc1)
      println(doc2.\("server").toString)
      list_servers = doc2.\("server").toString :: list_servers
    }
    Logger.debug("----Servers List----")
    Logger.debug(list_servers.toString)
    list_servers
  }

   
  def insertServerIPs(iplist: List[String]) = {

    val coll = ExtractorServer.dao.collection
    coll.drop()
    Logger.debug("extractor.servers: collection dropped.......")
    for (sip <- iplist) {
     ExtractorServer.insert(new ExtractorServer(sip), WriteConcern.Safe)
     Logger.debug("extractor.servers: document inserted : " + sip)
    }
    
  }

  def getExtractorNames() = {
    var list_queue = List[String]()

    Logger.debug("------getExtractorNames-----")
    var allDocs = ExtractorNames.dao.collection.find()
    for (doc <- allDocs) {
      var doc1 = com.mongodb.util.JSON.serialize(doc)
      var doc2 = Json.parse(doc1)
      println(doc2.\("name").toString)
      list_queue = doc2.\("name").toString :: list_queue
    }
    Logger.debug("----Extractor Name List----")
    Logger.debug(list_queue.toString)
    list_queue
  }

 
   def insertExtractorNames(exlist: List[String]) = {
    val qcoll = ExtractorNames.dao.collection
    qcoll.drop()
    Logger.debug("extractor.names: collection dropped.......")
    for (qn <- exlist) {
      Logger.debug("extractor.names: document inserted: " + qn)
      ExtractorNames.insert(new ExtractorNames(qn),WriteConcern.Safe)
      //qcoll.insert(MongoDBObject("name" -> qn))
    }
  }

  def getExtractorInputTypes() = {
    var list_inputs = List[String]()

    Logger.debug("------getExtractorInputTypes-----")
    var allDocs = ExtractorInputType.dao.collection.find()
    for (doc <- allDocs) {
      var doc1 = com.mongodb.util.JSON.serialize(doc)
      var doc2 = Json.parse(doc1)
      println(doc2.\("inputType").toString)
      list_inputs = doc2.\("inputType").toString :: list_inputs
    }
    Logger.debug("----Extractor Input List----")
    Logger.debug(list_inputs.toString)
    list_inputs
  }

 def insertInputTypes(inputTypes: List[String]) = {
    val inputcoll = ExtractorInputType.dao.collection
    inputcoll.drop()
    Logger.debug("extractor.inputtypes: collection dropped.......")
    for (ipt <- inputTypes) {
      ExtractorInputType.insert(new ExtractorInputType(ipt),WriteConcern.Safe)
      //inputcoll.insert(MongoDBObject("inputType" -> ipt))
      Logger.debug("extractor.inputtypes: document inserted: " + ipt)
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