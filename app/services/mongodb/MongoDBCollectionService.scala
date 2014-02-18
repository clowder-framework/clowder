/**
 *
 */
package services.mongodb

import models.Collection
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import java.text.SimpleDateFormat
import play.api.Logger
import models.Dataset
import scala.util.{Failure, Success, Try}
import services.{DatasetService, CollectionService}
import javax.inject.{Singleton, Inject}
import play.api.libs.json.Json._
import scala.util.Failure
import scala.Some
import scala.util.Success
import scala.util.Failure
import scala.Some
import scala.util.Success

/**
 * Use Mongodb to store collections.
 * 
 * @author Constantinos Sophocleous
 *
 */
@Singleton
class MongoDBCollectionService @Inject() (datasets: DatasetService)  extends CollectionService {
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

  def addDataset(collectionId: String, datasetId: String) = Try {
    Collection.findOneById(new ObjectId(collectionId)) match{
      case Some(collection) => {
        datasets.get(datasetId) match {
          case Some(dataset) => {
            if(!isInCollection(dataset,collection)){
              // add dataset to collection
              addDataset(collection.id.toString, dataset.id.toString)
              //add collection to dataset
              datasets.addCollection(dataset.id.toString, collection.id.toString)
              Logger.info("Adding dataset to collection completed")
            }
            else{
              Logger.info("Dataset was already in collection.")
            }
            Success
          }
          case None => {
            Logger.error("Error getting dataset " + datasetId)
            Failure
          }
        }
      }
      case None => {
        Logger.error("Error getting collection" + collectionId);
        Failure
      }
    }
  }

  def removeDataset(collectionId: String, datasetId: String, ignoreNotFound: Boolean = true) = Try {
    Collection.findOneById(new ObjectId(collectionId)) match{
      case Some(collection) => {
        datasets.get(datasetId) match {
          case Some(dataset) => {
            if(isInCollection(dataset,collection)){
              // remove dataset from collection
              Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId)), $addToSet("datasets" -> Dataset.toDBObject(dataset)), false, false, WriteConcern.Safe)
              //remove collection from dataset
              datasets.removeCollection(dataset.id.toString, collection.id.toString)
              Logger.info("Removing dataset from collection completed")
            }
            else{
              Logger.info("Dataset was already out of the collection.")
            }
            Success
          }
          case None => Success
        }
      }
      case None => {
        ignoreNotFound match{
          case true => Success
          case true =>
            Logger.error("Error getting collection" + collectionId)
            Failure
        }
      }
    }
  }

  private def isInCollection(dataset: Dataset, collection: Collection): Boolean = {
    for(collDataset <- collection.datasets){
      if(collDataset.id == dataset.id)
        return true
    }
    return false
  }

  def delete(collectionId: String) = Try {
    Collection.findOneById(new ObjectId(collectionId)) match {
      case Some(collection) => {
        for(dataset <- collection.datasets){
          //remove collection from dataset
          datasets.removeCollection(dataset.id.toString, collection.id.toString)
        }
        Collection.remove(MongoDBObject("_id" -> collection.id))
        Success
      }
      case None => Success
    }
  }

  def deleteAll() {
    Collection.remove(MongoDBObject())
  }

  def findOneByDatasetId(datasetId: String): Option[Collection] = {
    Collection.findOne(MongoDBObject("datasets._id" -> new ObjectId(datasetId)))
  }
}
