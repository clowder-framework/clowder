package services.mongodb

import java.text.SimpleDateFormat
import java.util.Date
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
import models.Role
import models.User

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
  datasets: DatasetService,
  users: UserService) extends SpaceService {

  def get(id: UUID): Option[ProjectSpace] = {
    ProjectSpaceDAO.findOneById(new ObjectId(id.stringify))
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def getCollectionsInSpace(space: Option[String], limit: Option[Integer]): List[Collection] = {
      collections.listCollections(limit, space)
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def getDatasetsInSpace(space: Option[String], limit: Option[Integer]): List[Dataset] = {
      datasets.listDatasets(limit, space)
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
    val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

    // Create sort object
    val d = if (direction == ASC) 1 else -1
    val o = order.getOrElse("created")
    val orderedBy = if (o == "created") {
      MongoDBObject(o -> d)
    } else {
      MongoDBObject(o -> d) ++ MongoDBObject("created" -> 1)
    }

    // set start and filter the data
    val spacesFromDB = (start, filter) match {
      case (Some(d), Some(f)) => {
        val since = "created" $gte sdf.parse(d)
        val filter = JSON.parse(f).asInstanceOf[DBObject]
        ProjectSpaceDAO.find(since ++ filter).sort(orderedBy).limit(limit).toList
      }
      case (Some(d), None) => {
        val since = "created" $gte sdf.parse(d)
        ProjectSpaceDAO.find(since).sort(orderedBy).limit(limit).toList
      }
      case (None, Some(f)) => {
        val filter = JSON.parse(f).asInstanceOf[DBObject]
        ProjectSpaceDAO.find(filter).sort(orderedBy).limit(limit).toList
      }
      case (None, None) => {
        ProjectSpaceDAO.findAll().sort(orderedBy).limit(limit).toList
      }
    }
    // update DOs with collection and dataset counts
    spacesFromDB.map{ s => s.copy(collectionCount = getCollectionsInSpace(Some(s.id.stringify)).size, datasetCount = getDatasetsInSpace(Some(s.id.stringify)).size) }
  }

  override def getNext(order: Option[String], direction: Direction, start: Date, limit: Integer,
                       filter: Option[String]): Option[String] = {
    val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

    // Create sort object
    val d = if (direction == ASC) 1 else -1
    val o = order.getOrElse("created")
    val orderedBy = if (o == "created") {
      MongoDBObject(o -> d)
    } else {
      MongoDBObject(o -> d) ++ MongoDBObject("created" -> 1)
    }

    // set start and filter the data
    val since = "created" $gt start
    val x = filter match {
      case Some(f) => {
        val filter = JSON.parse(f).asInstanceOf[DBObject]
        ProjectSpaceDAO.find(since ++ filter).sort(orderedBy).limit(1).toList
      }
      case None => {
        ProjectSpaceDAO.find(since).sort(orderedBy).limit(1).toList
      }
    }

    x.headOption.map(x => sdf.format(x.created))
  }

  override def getPrev(order: Option[String], direction: Direction, start: Date, limit: Integer,
                       filter: Option[String]): Option[String] = {
    val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

    // Create sort object
    val d = if (direction == ASC) -1 else 1
    val o = order.getOrElse("created")
    val orderedBy = if (o == "created") {
      MongoDBObject(o -> d)
    } else {
      MongoDBObject(o -> d) ++ MongoDBObject("created" -> -1)
    }

    // set start and filter the data
    val since = "created" $lt start
    val x = filter match {
      case Some(f) => {
        val filter = JSON.parse(f).asInstanceOf[DBObject]
        ProjectSpaceDAO.find(since ++ filter).sort(orderedBy).limit(limit).toList
      }
      case None => {
        ProjectSpaceDAO.find(since).sort(orderedBy).limit(limit).toList
      }
    }

    x.lastOption.map(x => sdf.format(x.created))
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
      val datasetsList = getDatasetsInSpace(Some(space.stringify))
      val collectionsList = getCollectionsInSpace(Some(space.stringify))
      val timeToLive = getTimeToLive(space)
      val currentTime = System.currentTimeMillis()

      for (aDataset <- datasetsList) {
    	  val datasetTime = aDataset.lastModifiedDate.getTime()
    	  val difference = currentTime - datasetTime
    	  if (difference > timeToLive) {
    	       //It was last modified longer than the time to live, so remove it.
    	       datasets.removeDataset(aDataset.id)
    	  }
      }

      for (aCollection <- collectionsList) {
          val collectionTime = aCollection.lastModifiedDate.getTime()
          val difference = currentTime - collectionTime
          if (difference > timeToLive) {
              //It was last modified longer than the time to live, so remiove it.
              for (colDataset <- aCollection.datasets) {
                  //Remove all the datasets in the collection if they don't have their own space.
                  colDataset.space match {
                      case Some(anId) => {
                          if (anId == space) {
                              //The dataset space id is the same, so go ahead and remove it as well.
                              datasets.removeDataset(colDataset.id)
                          }
                          //Nothing to be done in the else case, as it will simply detach the dataset when the collection is deleted.
                      }

                      case None => {
                          //In this case, the dataset is in the default space, so do not remove it, it will detach on collection deletion.
                          log.debug("collection being purged contained a dataset in the default space. The dataset will not be deleted.")
                      }
                  }
              }

              //Remove the collection. Any remaining datasets are to be simply detached.
              collections.delete(aCollection.id)
          }
      }
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def updateSpaceConfiguration(spaceId: UUID, name: String, description: String, timeToLive: Long, expireEnabled: Boolean) {
      val result = ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(spaceId.stringify)),
          $set("description" -> description, "name" -> name, "resourceTimeToLive" -> timeToLive, "isTimeToLiveEnabled" -> expireEnabled),
          false, false, WriteConcern.Safe);
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def addUser(user: UUID, role: Role, space: UUID): Unit = {
      users.addUserToSpace(user, role, space)
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def removeUser(userId: UUID, space: UUID): Unit = {
      users.removeUserFromSpace(userId, space)
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def getUsersInSpace(spaceId: UUID): List[User] = {
      var retList = users.listUsersInSpace(spaceId)
      retList
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def getRoleForUserInSpace(spaceId: UUID, userId: UUID): Option[Role] = {
      var retRole = users.getUserRoleInSpace(userId, spaceId)
      retRole
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def changeUserRole(userId: UUID, role: Role, space: UUID): Unit = {
      users.changeUserRoleInSpace(userId, role, space)
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
