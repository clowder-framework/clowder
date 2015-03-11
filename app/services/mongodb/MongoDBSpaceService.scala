package services.mongodb

import javax.inject.{Inject, Singleton}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON
import com.mongodb.DBObject
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{UserSpace, ProjectSpace, UUID}
import play.{Logger => log}
import play.api.Play._
import services._
import MongoContext.context
import util.Direction._
import models.Collection
import models.Dataset

/**
 * Store Spaces in MongoDB.
 *
 * @author Luigi Marini
 *
 */
@Singleton
class MongoDBSpaceService @Inject() (
  collections: CollectionService,
  files: FileService,
  datasets: DatasetService) extends SpaceService {

  def get(id: UUID): Option[ProjectSpace] = {
    ProjectSpaceDAO.findOneById(new ObjectId(id.stringify))
  }
  
  /**
   * @see app.services.SpaceService.scala
   * 
   * Implementation of the SpaceServie trait.
   * 
   */
  def getCollectionsInSpace(spaceId: UUID): List[Collection] = {
      collections.listCollectionsBySpace(spaceId)
  }
  
  /**
   * @see app.services.SpaceService.scala
   * 
   * Implementation of the SpaceServie trait.
   * 
   */
  def getDatasetsInSpace(spaceId: UUID): List[Dataset] = {
      datasets.listDatasetsBySpace(spaceId)
  }

  def insert(dataset: ProjectSpace): Option[String] = {
    ProjectSpaceDAO.insert(dataset).map(_.toString)
  }

  def update(space: ProjectSpace): Unit = {
    ProjectSpaceDAO.save(space)
  }

  def delete(id: UUID): Unit = {
    ProjectSpaceDAO.removeById(new ObjectId(id.stringify))
  }

  def list(): List[ProjectSpace] = {
    (for (space <- ProjectSpaceDAO.find(MongoDBObject())) yield space).toList
  }

  /**
   * The number of objects that are available based on the filter
   */
  override def count(filter: Option[String]): Long = {
    val filterBy = filter.fold(MongoDBObject())(JSON.parse(_).asInstanceOf[DBObject])
    ProjectSpaceDAO.count(filterBy)
  }

  /**
   * Return a list objects that are available based on the filter as well as the other options.
   *
   * @param order the key to use to order the data, default is natural ordering of underlying implementation
   * @param direction the direction to order the data in
   * @param start the first element that should be returned based on the order key
   * @param limit the maximum number of elements to return
   * @param filter is a json representation of the filter to be applied
   */
  override def list(order: Option[String], direction: Direction, start: Option[String], limit: Integer,
                    filter: Option[String]): List[ProjectSpace] = {
    val startAt = (order, start) match {
      case (Some(o), Some(s)) => {
        direction match {
          case ASC => (o $gte s)
          case DESC => (o $lte s)
        }
      }
      case (_, _) => MongoDBObject()
    }
    // what happens if we sort by user, and a user has uploaded 100 items?
    // how do we know that we need to show page 3 of that user?
    // TODO always sort by date ascending, start is based on user/start combo
    val filterBy = filter.fold(MongoDBObject())(JSON.parse(_).asInstanceOf[DBObject])
    val raw = ProjectSpaceDAO.find(startAt ++ filterBy)
    val orderedBy = order match {
      case Some(o) => {
        direction match {
          case ASC => raw.sort(MongoDBObject(o -> 1))
          case DESC => raw.sort(MongoDBObject(o -> -1))
        }
      }
      case None => raw
    }
    orderedBy.limit(limit).toList
  }

  /**
   * Associate a collection with a space
   *
   * @param collection collection id
   * @param space space id
   */
  def addCollection(collection: UUID, space: UUID): Unit = {
    log.debug(s"Adding $collection to $space")
    collections.addToSpace(collection, space)
  }

  /**
   * Associate a dataset with a space
   *
   * @param collection dataset id
   * @param space space id
   */
  def addDataset(dataset: UUID, space: UUID): Unit = {
    log.debug(s"Space Service - Adding $dataset to $space")
    datasets.addToSpace(dataset, space)
  }
  
  /**
   * Check if the time to live scope for a space is enabled.
   * 
   * @param space The id of the space to check
   * 
   * @return A Boolean, true if it is enabled, false otherwise or if there was an error
   * 
   */
  def isTimeToLiveEnabled(space: UUID): Boolean = {
      get(space) match {
          case Some(aSpace) => {
              return aSpace.isTimeToLiveEnabled
          }
          case None => {
              //Should this do something else other than log an error?
              log.error("Problem retrieving the space by ID in isTimeToLiveENabled")
        	  return false
          }
      }
  }
  
  /**
   * Retrieve the time to live value that a space is scoped by.
   * 
   * @param space The id of the space to check
   * 
   * @return An Integer that represents that lifetime of resources in whole days. 
   */
  def getTimeToLive(space: UUID): Long = {
      get(space) match {
          case Some(aSpace) => {
              return aSpace.resourceTimeToLive
          }
          case None => {
              //Should this do something else other than log and return -1?
              log.error("Problem retrieving the space by ID in getTimeToLive")
        	  return -1
          }
      }
  }
  
  
  /**
   * Go through the resources in the space, currently Collections and Datasets, and remove their contents if the
   * last modified time on them is older than the time to live that is scoping the space.
   * 
   *  @param space The id of the space to check
   *   
   */
  def purgeExpiredResources(space: UUID): Unit = {
      var datasetsList = getDatasetsInSpace(space)
      var collectionsList = getCollectionsInSpace(space)
      val timeToLive = getTimeToLive(space)
      val currentTime = System.currentTimeMillis()
      
      for (aDataset <- datasetsList) {
    	  val datasetTime = aDataset.lastModifiedDate.getTime()
    	  val difference = currentTime - datasetTime
    	  if (difference > timeToLive) {
    	       //It was last modified longer than the time to live, so remove it. Delete everything?
    	       datasets.removeDataset(aDataset.id)
    	  }
      }
      
      for (aCollection <- collectionsList) {
          val collectionTime = aCollection.lastModifiedDate.getTime()
          val difference = currentTime - collectionTime
          if (difference > timeToLive) {
              //It was last modified longer than the time to live, so remiove it.
              for (colDataset <- aCollection.datasets) {
                  //Remove all the datasets in the collection
                  datasets.removeDataset(colDataset.id)
              }
              
              //Remove the collection
              collections.delete(aCollection.id)
          }
      }
  }
  
  /**
   * @see app.services.SpaceService.scala
   * 
   * Implementation of the SpaceServie trait.
   * 
   */
  def updateSpaceConfiguration(spaceId: UUID, name: String, description: String, timeToLive: Long, expireEnabled: Boolean) {
      val result = ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(spaceId.stringify)), 
          $set("description" -> description, "name" -> name, "resourceTimeToLive" -> timeToLive, "isTimeToLiveEnabled" -> expireEnabled), 
          false, false, WriteConcern.Safe);
  }

}


/**
   * Salat ProjectSpace model companion.
   */
  object ProjectSpaceDAO extends ModelCompanion[ProjectSpace, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
  case Some(x) => new SalatDAO[ProjectSpace, ObjectId](collection = x.collection("spaces.projects")) {}
    }
  }

  /**
   * Salat UserSpace model companion.
   */
  object UserSpaceDAO extends ModelCompanion[UserSpace, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
  case Some(x) => new SalatDAO[UserSpace, ObjectId](collection = x.collection("spaces.users")) {}
    }
  }
