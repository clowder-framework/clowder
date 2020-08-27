package services.mongodb

import java.net.InetAddress
import javax.inject.{Inject, Singleton}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import salat.dao.{ModelCompanion, SalatDAO}
import models._
import org.bson.types.ObjectId
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.{JsArray, JsNumber, JsObject, JsString, JsValue, Json}
import play.api.libs.concurrent.Execution.Implicits._
import services._
import services.mongodb.MongoContext.context

@Singleton
class MongoDBExtractorService extends ExtractorService {

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

  def getExtractorInputTypes() = {
    var list_inputs = List[String]()
    ExtractorInfoDAO.findAll().foreach(ext_info => {
      ext_info.process.dataset.foreach(proc => {
        list_inputs = "dataset."+proc :: list_inputs
      })
      ext_info.process.file.foreach(proc => {
        list_inputs = "file."+proc :: list_inputs
      })
      ext_info.process.metadata.foreach(proc => {
        list_inputs = "metadata."+proc :: list_inputs
      })
    })
    list_inputs.distinct
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

/**
 * Data Access Object for ExtractorDetail
 */
object ExtractorInfoDAO extends ModelCompanion[ExtractorInfo, ObjectId] {
  val COLLECTION = "extractors.info"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[ExtractorInfo, ObjectId](collection = mongos.collection(COLLECTION)) {}
}

object ExtractorsForInstanceDAO extends ModelCompanion[ExtractorsForInstance, ObjectId] {
  val COLLECTION = "extractors.global"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[ExtractorsForInstance, ObjectId](collection = mongos.collection(COLLECTION)) {}
}

