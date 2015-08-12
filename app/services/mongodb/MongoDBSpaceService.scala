package services.mongodb

import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.{Inject, Singleton}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON
import com.mongodb.DBObject
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models._
import org.bson.types.ObjectId
import play.api.Logger
import play.{Logger => log}
import play.api.Play._
import securesocial.core.providers.Token
import services._
import MongoContext.context
import util.Direction._
import models.Collection
import models.Dataset
import models.Role
import models.User
import util.Formatters
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
    ProjectSpaceDAO.findOneById(new ObjectId(id.stringify)).map(fixSpaceCounts)
  }

  /**
   * Count all spaces the user has access to.
   */
  def countAccess(user: Option[User], superAdmin: Boolean): Long = {
    count(None, false, user, superAdmin, None)
  }

  /**
   * Return a list of spaces the user has access to.
   */
  def listAccess(limit: Integer, user: Option[User], superAdmin: Boolean): List[ProjectSpace] = {
    list(None, false, limit, user, superAdmin, None)
  }

  /**
   * Return a list of spaces the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, user: Option[User], superAdmin: Boolean): List[ProjectSpace] = {
    list(Some(date), nextPage, limit, user, superAdmin, None)
  }

  /**
   * Count all spaces the user has created.
   */
  def countUser(user: Option[User], superAdmin: Boolean, owner: User): Long = {
    count(None, false, user, superAdmin, Some(owner))
  }

  /**
   * Return a list of spaces the user has created.
   */
  def listUser(limit: Integer, user: Option[User], superAdmin: Boolean, owner: User): List[ProjectSpace] = {
    list(None, false, limit, user, superAdmin, Some(owner))
  }

  /**
   * Return a list of spaces the user has created starting at a specific date.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, user: Option[User], superAdmin: Boolean, owner: User): List[ProjectSpace] = {
    list(Some(date), nextPage, limit, user, superAdmin, Some(owner))
  }

  /**
   * return count based on input
   */
  private def count(date: Option[String], nextPage: Boolean, user: Option[User], superAdmin: Boolean, owner: Option[User]): Long = {
    val (filter, _) = filteredQuery(date, nextPage, user, superAdmin, owner)
    ProjectSpaceDAO.count(filter)
  }


  /**
   * return list based on input
   */
  private def list(date: Option[String], nextPage: Boolean, limit: Integer, user: Option[User], superAdmin: Boolean, owner: Option[User]): List[ProjectSpace] = {
    val (filter, sort) = filteredQuery(date, nextPage, user, superAdmin, owner)
    if (date.isEmpty || nextPage) {
      ProjectSpaceDAO.find(filter).sort(sort).limit(limit).toList.map(fixSpaceCounts)
    } else {
      ProjectSpaceDAO.find(filter).sort(sort).limit(limit).toList.reverse.map(fixSpaceCounts)
    }
  }

  /**
   * Monster function, does all the work. Will create a filters and sorts based on the given parameters
   */
  private def filteredQuery(date: Option[String], nextPage: Boolean, user: Option[User], superAdmin: Boolean, owner: Option[User]): (DBObject, DBObject) = {
    // filter =
    // - owner   == show datasets owned by owner that user can see
    // - space   == show all datasets in space
    // - access  == show all datasets the user can see
    // - default == public only
    val public = MongoDBObject("public" -> true)
    val filter = owner match {
      case Some(o) => {
        val author = MongoDBObject("author.identityId.userId" -> o.identityId.userId) ++ MongoDBObject("author.identityId.providerId" -> o.identityId.providerId)
        user match {
          case Some(u) => {
            if (superAdmin) {
              author
            } else {
              author ++ $or(public, ("_id" $in u.spaceandrole.map(x => new ObjectId(x.spaceId.stringify))))
            }
          }
          case None => {
            author ++ public
          }
        }
      }
      case None => {
        user match {
          case Some(u) => {
            val author = $and(MongoDBObject("author.identityId.userId" -> u.identityId.userId) ++ MongoDBObject("author.identityId.providerId" -> u.identityId.providerId))
            $or(author, public, ("_id" $in u.spaceandrole.map(x => new ObjectId(x.spaceId.stringify))))
          }
          case None => public
        }
      }
    }
    val filterDate = date match {
      case Some(d) => {
        if (nextPage) {
          ("created" $lt Formatters.iso8601(d))
        } else {
          ("created" $gt Formatters.iso8601(d))
        }
      }
      case None => {
        MongoDBObject()
      }
    }

    val sort = if (date.isDefined && !nextPage) {
      MongoDBObject("created"-> 1) ++ MongoDBObject("name" -> 1)
    } else {
      MongoDBObject("created" -> -1) ++ MongoDBObject("name" -> 1)
    }

    (filter ++ filterDate, sort)
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def getCollectionsInSpace(space: Option[String], limit: Option[Integer]): List[Collection] = {
      collections.listSpace(limit.getOrElse(12), space.getOrElse(""))
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def getDatasetsInSpace(space: Option[String], limit: Option[Integer]): List[Dataset] = {
      datasets.listSpace(limit.getOrElse(12), space.getOrElse(""))
  }
  
  def insert(space: ProjectSpace): Option[String] = {
    ProjectSpaceDAO.insert(space).map(_.toString)
  }

  def update(space: ProjectSpace): Unit = {
    ProjectSpaceDAO.save(space)
  }

  def delete(id: UUID): Unit = {
    ProjectSpaceDAO.removeById(new ObjectId(id.stringify))
  }

  def list(): List[ProjectSpace] = {
    (for (space <- ProjectSpaceDAO.find(MongoDBObject())) yield space).toList.map(fixSpaceCounts)
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
    spacesFromDB.map(fixSpaceCounts)
  }

  private def fixSpaceCounts(space: ProjectSpace): ProjectSpace = {
    val datasetsCount = datasets.countSpace(space.id.stringify)
    val collectionsCount = collections.countSpace(space.id.stringify)
    space.copy(collectionCount=collectionsCount.toInt, datasetCount=datasetsCount.toInt)
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
        ProjectSpaceDAO.find(since ++ filter).sort(orderedBy).limit(1).toList.map(fixSpaceCounts)
      }
      case None => {
        ProjectSpaceDAO.find(since).sort(orderedBy).limit(1).toList.map(fixSpaceCounts)
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
        ProjectSpaceDAO.find(since ++ filter).sort(orderedBy).limit(limit).toList.map(fixSpaceCounts)
      }
      case None => {
        ProjectSpaceDAO.find(since).sort(orderedBy).limit(limit).toList.map(fixSpaceCounts)
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

  def removeCollection(collection:UUID, space:UUID): Unit = {
    log.debug(s"Space Service - removing $collection from $space")
    collections.removeFromSpace(collection, space)
  }
  /**
   * Associate a dataset with a space
   *
   * @param dataset dataset id
   * @param space space id
   */
  def addDataset(dataset: UUID, space: UUID): Unit = {
    log.debug(s"Space Service - Adding $dataset to $space")
    datasets.addToSpace(dataset, space)
  }

  /**
   * Remove association betweren dataset and a space
   * @param dataset dataset id
   * @param space space id
   */
  def removeDataset(dataset:UUID, space:UUID): Unit = {
    log.debug(s"Space Service - removing $dataset from $space")
    datasets.removeFromSpace(dataset, space)
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
                var datasetOnlyInSpace: Option[Boolean] = None
                colDataset.spaces.map {
                      // Id the dataset exists in a space different than the one being detached, we want to keep the dataset.
                  spaceId => if(spaceId != space) {
                    datasetOnlyInSpace = Some(false)
                  } else {
                    //Detach the dataset from the space that is currently being etached.
                    datasets.removeFromSpace(colDataset.id, spaceId)
                    datasetOnlyInSpace match {
                        //We only want to set this as true, if it was None, if it was false, we don't want to indicate that ths=is is the only space the dataset is in.
                      case None => datasetOnlyInSpace = Some(true)
                    }
                  }
                }
                datasetOnlyInSpace match {
                  case Some(true) => {
                    //If the dataset only exists in the current space, it can be removed.
                    datasets.removeDataset(colDataset.id)
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

  def addFollower(id: UUID, userId: UUID) {
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $addToSet("followers" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)
  }

  def removeFollower(id: UUID, userId: UUID) {
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $pull("followers" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)
  }

    def addRequest(id: UUID, userId: UUID, username: String) {
    Logger.debug("put request for a space")
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $addToSet("requests"-> MongoDBObject("_id" -> new ObjectId(userId.stringify), "name" -> username, "comment" -> "N/A" )), false, false, WriteConcern.Safe)
  }

  def removeRequest(id: UUID, userId: UUID) {
    Logger.debug("remove request for a space ")
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $pull("requests" -> MongoDBObject( "_id" -> new ObjectId(userId.stringify))), false, false, WriteConcern.Safe)
  }


  def addInvitationToSpace(invite: SpaceInvite) {
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(invite.space.stringify)),
      $addToSet("invitations"-> MongoDBObject("_id" -> new ObjectId(invite.id.stringify), "role" -> invite.role )), false, false, WriteConcern.Safe)
    SpaceInviteDAO.insert(invite)
  }

  def  removeInvitationToSpace(inviteId: UUID, spaceId: UUID) {
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(spaceId.stringify)),
      $pull("invitations" -> MongoDBObject( "_id" -> new ObjectId(inviteId.stringify))), false, false, WriteConcern.Safe)
    SpaceInviteDAO.removeById(new ObjectId(inviteId.stringify))
  }

  def getInvitationToSpace(inviteId: String): Option[SpaceInvite] = {
    SpaceInviteDAO.findOne(MongoDBObject("invite_id" -> inviteId))
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

  object SpaceInviteDAO extends ModelCompanion[SpaceInvite, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No mongoSalatPlugin");
      case Some(x) => new SalatDAO[SpaceInvite, ObjectId](collection = x.collection("spaces.invites")) {}
    }
  }