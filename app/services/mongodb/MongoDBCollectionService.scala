/**
 *
 */
package services.mongodb

import models.{UUID, Collection, Dataset}
import com.mongodb.casbah.commons.MongoDBObject
import java.text.SimpleDateFormat
import play.api.Logger
import scala.util.Try
import services.{DatasetService, CollectionService}
import javax.inject.{Singleton, Inject}
import scala.util.Failure
import scala.Some
import scala.util.Success
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.Imports._
import MongoContext.context
import play.api.Play.current
import services.AdminsNotifierPlugin
import services.ElasticsearchPlugin



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
  def get(id: UUID): Option[Collection] = {
    Collection.findOneById(new ObjectId(id.stringify))
  }

  def latest(): Option[Collection] = {
    val results = Collection.find(MongoDBObject()).sort(MongoDBObject("created" -> -1)).limit(1).toList
    if (results.size > 0)
      Some(results(0))
    else
      None
  }

  def first(): Option[Collection] = {
    val results = Collection.find(MongoDBObject()).sort(MongoDBObject("created" -> 1)).limit(1).toList
    if (results.size > 0)
      Some(results(0))
    else
      None
  }

  def insert(collection: Collection): Option[String] = {
    Collection.insert(collection).map(_.toString)
  }

  /**
   * List all collections outside a dataset.
   */
  def listOutsideDataset(datasetId: UUID): List[Collection] = {
    Dataset.findOneById(new ObjectId(datasetId.stringify)) match {
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
  def listInsideDataset(datasetId: UUID): List[Collection] = {
    Dataset.findOneById(new ObjectId(datasetId.stringify)) match {
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
    for (dsColls <- dataset.collections  ) {
      if (dsColls == collection.id.stringify)
        return true
    }
    return false
  }

  def addDataset(collectionId: UUID, datasetId: UUID) = Try {
    Logger.debug(s"Adding dataset $datasetId to collection $collectionId")
    Collection.findOneById(new ObjectId(collectionId.stringify)) match{
      case Some(collection) => {
        datasets.get(datasetId) match {
          case Some(dataset) => {
            if(!isInCollection(dataset,collection)){
              // add dataset to collection
              Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)),
                $addToSet("datasets" ->  Dataset.toDBObject(dataset)), false, false, WriteConcern.Safe)
              //add collection to dataset
              datasets.addCollection(dataset.id, collection.id)
              
              if(collection.thumbnail_id.isEmpty && !dataset.thumbnail_id.isEmpty){ 
                  Collection.dao.collection.update(MongoDBObject("_id" -> new ObjectId(collection.id.stringify)), 
                  $set("thumbnail_id" -> dataset.thumbnail_id.get), false, false, WriteConcern.Safe)
              }

              datasets.index(dataset.id)
              index(collection.id)

              Logger.debug("Adding dataset to collection completed")
            }
            else{
              Logger.debug("Dataset was already in collection.")
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

  def removeDataset(collectionId: UUID, datasetId: UUID, ignoreNotFound: Boolean = true) = Try {
    Collection.findOneById(new ObjectId(collectionId.stringify)) match{
      case Some(collection) => {
        datasets.get(datasetId) match {
          case Some(dataset) => {
            if(isInCollection(dataset,collection)){
              // remove dataset from collection
              Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)),
            		  $pull("datasets" ->  MongoDBObject( "_id" -> new ObjectId(dataset.id.stringify))), false, false, WriteConcern.Safe)
              //remove collection from dataset
              datasets.removeCollection(dataset.id, collection.id)
              
              if(!collection.thumbnail_id.isEmpty && !dataset.thumbnail_id.isEmpty){
	        	  if(collection.thumbnail_id.get == dataset.thumbnail_id.get){
	        		  createThumbnail(collection.id)
	        	  }		                        
	          }
              
              datasets.index(dataset.id)
              index(collection.id)

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
          case false =>
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

  def delete(collectionId: UUID) = Try {
    Collection.findOneById(new ObjectId(collectionId.stringify)) match {
      case Some(collection) => {
        for(dataset <- collection.datasets){
          //remove collection from dataset
          datasets.removeCollection(dataset.id, collection.id)
          datasets.index(dataset.id)
        }
        Collection.remove(MongoDBObject("_id" -> new ObjectId(collection.id.stringify)))

        current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("Collection","removed",collection.id.stringify, collection.name)}
        current.plugin[ElasticsearchPlugin].foreach {
          _.delete("data", "collection", collection.id.stringify)
        }

        Success
      }
      case None => Success
    }
  }

  def deleteAll() {
    Collection.remove(MongoDBObject())
  }

  def findOneByDatasetId(datasetId: UUID): Option[Collection] = {
    Collection.findOne(MongoDBObject("datasets._id" -> new ObjectId(datasetId.stringify)))
  }
  
  def updateThumbnail(collectionId: UUID, thumbnailId: UUID) {
    Collection.dao.collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)),
      $set("thumbnail_id" -> thumbnailId.stringify), false, false, WriteConcern.Safe)
  }
  
  def createThumbnail(collectionId:UUID){
    get(collectionId) match{
	    case Some(collection) => {
	    		val selecteddatasets = collection.datasets map { ds =>{
	    			datasets.get(ds.id).getOrElse{None}
	    		}}
			    for(dataset <- selecteddatasets){
			      if(dataset.isInstanceOf[models.Dataset]){
			          val theDataset = dataset.asInstanceOf[models.Dataset]
				      if(!theDataset.thumbnail_id.isEmpty){
				        Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)), $set("thumbnail_id" -> theDataset.thumbnail_id.get), false, false, WriteConcern.Safe)
				        return
				      }
			      }
			    }
			    Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)), $set("thumbnail_id" -> None), false, false, WriteConcern.Safe)
	    }
	    case None =>
    }
  }


  def index(id: UUID) {
    Collection.findOneById(new ObjectId(id.stringify)) match {
      case Some(collection) => {
        
        var dsCollsId = ""
        var dsCollsName = ""
          
        for(dataset <- collection.datasets){
          dsCollsId = dsCollsId + dataset.id.stringify + " %%% "
          dsCollsName = dsCollsName + dataset.name + " %%% "
        }
	    
	    val formatter = new SimpleDateFormat("dd/MM/yyyy")

        current.plugin[ElasticsearchPlugin].foreach {
          _.index("data", "collection", id,
            List(("name", collection.name), ("description", collection.description), ("created",formatter.format(collection.created)), ("datasetId",dsCollsId),("datasetName",dsCollsName)))
        }
      }
      case None => Logger.error("Collection not found: " + id.stringify)
    }
  }
}

object Collection extends ModelCompanion[Collection, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Collection, ObjectId](collection = x.collection("collections")) {}
  }
}
