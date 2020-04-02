package services.mongodb

import javax.inject.Singleton
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import models._
import org.bson.types.ObjectId
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.{JsArray, JsNumber, JsObject, JsString, JsValue, Json}
import services._
import services.mongodb.MongoContext.context

@Singleton
class MongoDBExtractorService extends ExtractorService {

  def getExtractorServerIPList() = {
    var listServersIPs = List[String]()
    Logger.debug("[mongodbextractorservice]--getServerIPList()")
    var allDocs = ExtractorServer.dao.collection.find()
    for (doc <- allDocs) {
      var doc1 = com.mongodb.util.JSON.serialize(doc)
      var doc2 = Json.parse(doc1)
      var doc3 = doc2.\("server").asOpt[String]
      doc3.map {
        t => listServersIPs = t :: listServersIPs
      }.getOrElse {}
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

  /**
    * Disables all extractors on this instance.
    */
  def disableAllExtractors(): Boolean = {
    val query = MongoDBObject()
    val result = ExtractorsForInstanceDAO.remove(query)
    //if one or more deleted - return true
    val wasDeleted = result.getN >0
    wasDeleted
  }

  /**
    * Retrieves a list of the names of all enabled extractors on this instance.
    */
  def getEnabledExtractors(): List[String] = {
    //Note: in models.ExtractorsForSpace, spaceId must be a String
    // if space Id is UUID, will compile but throws Box run-time error
    val query = MongoDBObject()

    val list = (for (extr <- ExtractorsForInstanceDAO.find(query)) yield extr).toList
    //get extractors' names for given space id
    val extractorList: List[String] = list.flatMap(_.extractors)
    extractorList
  }

  /**
    * Adds this extractor to the globally-enabled extractor list for this instance.
    */
  def enableExtractor(extractor: String) {
    //will add extractor to the list of extractors for this space, only if it's not there.
    val query = MongoDBObject()
    ExtractorsForInstanceDAO.update(query, $addToSet("extractors" -> extractor), true, false, WriteConcern.Safe)
  }

  def getExtractorNames(categories: List[String]) = {
    var list_queue = List[String]()

    val allDocs = ExtractorInfoDAO.findAll()
    for (doc <- allDocs) {
      // If no categories are specified, return all extractor names
      var category_match = categories.isEmpty
      if (!category_match) {
        // Otherwise check if any extractor categories overlap requested categories (force uppercase)
        val upper_categories = categories.map(cat => cat.toUpperCase)
        category_match = doc.categories.intersect(upper_categories).length > 0
      }

      if (category_match)
        list_queue = doc.name :: list_queue
    }
    list_queue.distinct
  }


  def insertExtractorNames(exlist: List[String]) = {
    for (qn <- exlist) {
      Logger.debug("[mongodbextractorservice]--extractor.names: document inserted: " + qn)
      ExtractorNames.insert(new ExtractorNames(qn), WriteConcern.Safe)
    }
  }

  def dropAllExtractorStatusCollection() = {
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
   * This is a temporary fix for keeping track of extractor servers IPs, names and the extractors' counts 
   * This will be omitted in future version 
   *
   * * 
   */
  def insertExtractorDetail(exDetails: List[ExtractorDetail]) = {
    for (qn <- exDetails) {
      Logger.debug("[mongodbextractorservice]--extractor.details: document inserted: " + qn)
      ExtractorDetailDAO.insert(qn, WriteConcern.Safe)
    }
  }

  def getExtractorDetail(): Option[JsValue] = {
    var edArray = new JsArray()
    Logger.debug("[MongoDBExtractorService]- getExtractorDetails")
    var allDocs = ExtractorDetailDAO.findAll
    for (doc <- allDocs) {
      edArray = edArray :+ JsObject(Seq("ip" -> JsString(doc.ip), "extractor_name" -> JsString(doc.name), "count" -> JsNumber(doc.count)))
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
      var doc3 = doc2.\("inputType").asOpt[String]
      doc3.map {
        t => list_inputs = t :: list_inputs
      }.getOrElse {}
    }
    Logger.debug("[mongodbextractorservice]--Extractor Input List")
    Logger.debug(list_inputs.toString)
    list_inputs
  }

  def insertInputTypes(inputTypes: List[String]) = {
    for (ipt <- inputTypes) {
      ExtractorInputType.insert(new ExtractorInputType(ipt), WriteConcern.Safe)
      Logger.debug("[mongodbextractorservice]--extractor.inputtypes: document inserted: " + ipt)
    }
  }

  def listExtractorsInfo(categories: List[String]): List[ExtractorInfo] = {
    var list_queue = List[ExtractorInfo]()

    val allDocs = ExtractorInfoDAO.findAll()
    for (doc <- allDocs) {
      // If no categories are specified, return all extractor names
      var category_match = categories.isEmpty
      if (!category_match) {
        // Otherwise check if any extractor categories overlap requested categories (force uppercase)
        val upper_categories = categories.map(cat => cat.toUpperCase)
        category_match = doc.categories.intersect(upper_categories).length > 0
      }

      if (category_match)
        list_queue = doc :: list_queue
    }

    list_queue
  }

  def getExtractorInfo(extractorName: String): Option[ExtractorInfo] = {
    ExtractorInfoDAO.findOne(MongoDBObject("name" -> extractorName))
  }

  def updateExtractorInfo(e: ExtractorInfo): Option[ExtractorInfo] = {
    ExtractorInfoDAO.findOne(MongoDBObject("name" -> e.name)) match {
      case Some(old) => {
        val updated = e.copy(id = old.id)
        ExtractorInfoDAO.update(MongoDBObject("name" -> e.name), updated, false, false, WriteConcern.Safe)
        Some(updated)
      }
      case None => {
        ExtractorInfoDAO.save(e)
        Some(e)
      }
    }
  }

  def deleteExtractor(extractorName: String) {
    ExtractorInfoDAO.findOne(MongoDBObject("name" -> extractorName)) match {
      case Some(extractor) => {
        ExtractorInfoDAO.remove(MongoDBObject("name" -> extractor.name))
      }
      case None => {
        Logger.info("No extractor found with name: " + extractorName)
      }

    }
  }
}

object ExtractorServer extends ModelCompanion[ExtractorServer, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ExtractorServer, ObjectId](collection = x.collection("extractor.servers")) {}
  }
}

object ExtractorNames extends ModelCompanion[ExtractorNames, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ExtractorNames, ObjectId](collection = x.collection("extractor.names")) {}
  }
}

object ExtractorInputType extends ModelCompanion[ExtractorInputType, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ExtractorInputType, ObjectId](collection = x.collection("extractor.inputtypes")) {}
  }
}

/**
 * Temporary Fix: Creating a mongodb collection for keeping track of extractors details
 */
object ExtractorDetailDAO extends ModelCompanion[ExtractorDetail, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ExtractorDetail, ObjectId](collection = x.collection("extractor.details")) {}
  }
}

/**
 * Data Access Object for ExtractorDetail
 */
object ExtractorInfoDAO extends ModelCompanion[ExtractorInfo, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ExtractorInfo, ObjectId](collection = x.collection("extractors.info")) {}
  }
}

object ExtractorsForInstanceDAO extends ModelCompanion[ExtractorsForInstance, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ExtractorsForInstance, ObjectId](collection = x.collection("extractors.global")) {}
  }
}

