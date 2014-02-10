/**
 *
 */
package services
import org.bson.types.ObjectId
import models.Collection
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import java.text.SimpleDateFormat
import play.api.Logger
import models.Dataset

/**
 * Implementation of DatasetService using Mongodb.
 *
 * @author Constantinos Sophocleous
 *
 */
trait MongoDBCollection {
  
  /**
   * List all collections in the system.
   */
  def listCollections(): List[Collection] = {
    (for (collection <- Collection.find(MongoDBObject())) yield collection).toList
  }

  /**
   * List all collections in the system in reverse chronological order.
   */
  def listCollectionsChronoReverse(): List[Collection] = {
    val order = MongoDBObject("created" -> -1)
    Collection.findAll.sort(order).toList
  }

  /**
   * List collections after a specified date.
   */
  def listCollectionsAfter(date: String, limit: Int): List[Collection] = {
    val order = MongoDBObject("created" -> -1)
    if (date == "") {
      Collection.findAll.sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.info("After " + sinceDate)
      Collection.find("created" $lt sinceDate).sort(order).limit(limit).toList
    }
  }

  /**
   * List collections before a specified date.
   */
  def listCollectionsBefore(date: String, limit: Int): List[Collection] = {
    var order = MongoDBObject("created" -> -1)
    if (date == "") {
      Collection.findAll.sort(order).limit(limit).toList
    } else {
      order = MongoDBObject("created" -> 1)
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.info("Before " + sinceDate)
      var collectionList = Collection.find("created" $gt sinceDate).sort(order).limit(limit + 1).toList.reverse
      collectionList = collectionList.filter(_ != collectionList.last)
      collectionList
    }
  }

  /**
   * Get collection.
   */
  def get(id: String): Option[Collection] = {
    Collection.findOneById(new ObjectId(id))
  }

  /**
   * List all collections outside a dataset.
   */
  def listOutsideDataset(datasetId: String): List[Collection] = {
    Dataset.findOneById(new ObjectId(datasetId)) match {
      case Some(dataset) => {
        val list = for (collection <- listCollections(); if (!isInDataset(dataset, collection))) yield collection
        return list.reverse
      }
      case None => {
        val list = for (collection <- listCollections()) yield collection
        return list.reverse
      }
    }
  }

  /**
   * List all collections inside a dataset.
   */
  def listInsideDataset(datasetId: String): List[Collection] = {
    Dataset.findOneById(new ObjectId(datasetId)) match {
      case Some(dataset) => {
        val list = for (collection <- listCollections(); if (isInDataset(dataset, collection))) yield collection
        return list.reverse
      }
      case None => {
        val list = for (collection <- listCollections()) yield collection
        return list.reverse
      }
    }
  }

  def isInDataset(dataset: Dataset, collection: Collection): Boolean = {
    for (dsColls <- dataset.collections) {
      if (dsColls == collection.id.toString())
        return true
    }
    return false
  }
}
