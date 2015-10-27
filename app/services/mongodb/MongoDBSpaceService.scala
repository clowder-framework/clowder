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
    ProjectSpaceDAO.findOneById(new ObjectId(id.stringify))
  }

  /** count all spaces */
  def count(): Long = {
    count(None, nextPage=false, None, showAll=true, None)
  }

  /** list all spaces */
  def list(): List[ProjectSpace] = {
    list(None, nextPage=false, 0, None, showAll=true, None)
  }

  /**
   * Count all spaces the user has access to.
   */
  def countAccess(user: Option[User], showAll: Boolean): Long = {
    count(None, nextPage=false, user, showAll, None)
  }

  /**
   * Return a list of spaces the user has access to.
   */
  def listAccess(limit: Integer, user: Option[User], showAll: Boolean): List[ProjectSpace] = {
    list(None, nextPage=false, limit, user, showAll, None)
  }

  /**
   * Return a list of spaces the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean): List[ProjectSpace] = {
    list(Some(date), nextPage, limit, user, showAll, None)
  }

  /**
   * Count all spaces the user has created.
   */
  def countUser(user: Option[User], showAll: Boolean, owner: User): Long = {
    count(None, nextPage=false, user, showAll, Some(owner))
  }

  /**
   * Return a list of spaces the user has created.
   */
  def listUser(limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[ProjectSpace] = {
    list(None, nextPage=false, limit, user, showAll, Some(owner))
  }

  /**
   * Return a list of spaces the user has created starting at a specific date.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[ProjectSpace] = {
    list(Some(date), nextPage, limit, user, showAll, Some(owner))
  }

  /**
   * return count based on input
   */
  private def count(date: Option[String], nextPage: Boolean, user: Option[User], showAll: Boolean, owner: Option[User]): Long = {
    val (filter, _) = filteredQuery(date, nextPage, user, showAll, owner)
    ProjectSpaceDAO.count(filter)
  }


  /**
   * return list based on input
   */
  private def list(date: Option[String], nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean, owner: Option[User]): List[ProjectSpace] = {
    val (filter, sort) = filteredQuery(date, nextPage, user, showAll, owner)
    if (date.isEmpty || nextPage) {
      ProjectSpaceDAO.find(filter).sort(sort).limit(limit).toList
    } else {
      ProjectSpaceDAO.find(filter).sort(sort).limit(limit).toList.reverse
    }
  }

  /**
   * Monster function, does all the work. Will create a filters and sorts based on the given parameters
   */
  private def filteredQuery(date: Option[String], nextPage: Boolean, user: Option[User], showAll: Boolean, owner: Option[User]): (DBObject, DBObject) = {
    // filter =
    // - owner   == show datasets owned by owner that user can see
    // - space   == show all datasets in space
    // - access  == show all datasets the user can see
    // - default == public only
    val public = MongoDBObject("public" -> true)
    val filter = owner match {
      case Some(o) => {
        val author = MongoDBObject("author.identityId.userId" -> o.identityId.userId) ++ MongoDBObject("author.identityId.providerId" -> o.identityId.providerId)
        if (showAll) {
          author
        } else {
          user match {
            case Some(u) => {
              author ++ $or(public, ("_id" $in u.spaceandrole.map(x => new ObjectId(x.spaceId.stringify))))
            }
            case None => {
              author ++ public
            }
          }
        }
      }
      case None => {
        if (showAll) {
          MongoDBObject()
        } else {
          user match {
            case Some(u) => {
              val author = $and(MongoDBObject("author.identityId.userId" -> u.identityId.userId) ++ MongoDBObject("author.identityId.providerId" -> u.identityId.providerId))
              $or(author, public, ("_id" $in u.spaceandrole.map(x => new ObjectId(x.spaceId.stringify))))
            }
            case None => public
          }
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

  /**
   * Associate a collection with a space
   *
   * @param collection collection id
   * @param space space id
   */
  def addCollection(collection: UUID, space: UUID): Unit = {
    log.debug(s"Adding $collection to $space")
    collections.addToSpace(collection, space)
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(space.stringify)), $inc("collectionCount" -> 1), upsert=false, multi=false, WriteConcern.Safe)
  }

  def removeCollection(collection:UUID, space:UUID): Unit = {
    log.debug(s"Space Service - removing $collection from $space")
    collections.removeFromSpace(collection, space)
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(space.stringify)), $inc("collectionCount" -> -1), upsert=false, multi=false, WriteConcern.Safe)
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
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(space.stringify)), $inc("datasetCount" -> 1), upsert=false, multi=false, WriteConcern.Safe)
  }

  /**
   * Remove association betweren dataset and a space
   * @param dataset dataset id
   * @param space space id
   */
  def removeDataset(dataset:UUID, space:UUID): Unit = {
    log.debug(s"Space Service - removing $dataset from $space")
    datasets.removeFromSpace(dataset, space)
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(space.stringify)), $inc("datasetCount" -> -1), upsert=false, multi=false, WriteConcern.Safe)
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
          false, false, WriteConcern.Safe)
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def addUser(user: UUID, role: Role, space: UUID): Unit = {
    users.addUserToSpace(user, role, space)
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(space.stringify)), $inc("userCount" -> 1), upsert=false, multi=false, WriteConcern.Safe)
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def removeUser(userId: UUID, space: UUID): Unit = {
    users.removeUserFromSpace(userId, space)
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(space.stringify)), $inc("userCount" -> -1), upsert=false, multi=false, WriteConcern.Safe)
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

  def addCurationObject(spaceId: UUID, curationObjectId: UUID) {
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(spaceId.stringify)),
    $addToSet("curationObjects" -> new ObjectId(curationObjectId.stringify)), false, false, WriteConcern.Safe)
  }

  def removeCurationObject(spaceId: UUID, curationObjectId: UUID) {
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(spaceId.stringify)),
      $pull("curationObjects" -> new ObjectId(curationObjectId.stringify)), false, false, WriteConcern.Safe)
  }

  def updateUserCount(space: UUID, numberOfUser:Int):Unit ={
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(space.stringify)), $set("userCount" ->numberOfUser), false, false, WriteConcern.Safe)
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

  def removeInvitationFromSpace(inviteId: UUID, spaceId: UUID) {
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(spaceId.stringify)),
      $pull("invitations" -> MongoDBObject( "_id" -> new ObjectId(inviteId.stringify))), false, false, WriteConcern.Safe)
    SpaceInviteDAO.removeById(new ObjectId(inviteId.stringify))
  }

  def getInvitation(inviteId: String): Option[SpaceInvite] = {
    SpaceInviteDAO.findOne(MongoDBObject("invite_id" -> inviteId))
  }

  def getInvitationBySpace(space: UUID): List[SpaceInvite] = {
    SpaceInviteDAO.find(MongoDBObject("space" -> new ObjectId(space.stringify))).toList
  }

  def getInvitationByEmail(email: String): List[SpaceInvite] = {
    SpaceInviteDAO.find(MongoDBObject("email" -> email)).toList
  }

  /**
   * Deletes entry with this space id.
   */
  def deleteAllExtractors(spaceId: UUID): Boolean = {    
    val query = MongoDBObject("spaceId" -> spaceId.stringify)
    val result = ExtractorsForSpaceDAO.remove( query )
    //if one or more deleted - return true
    val wasDeleted = result.getN >0        
    wasDeleted
  }  
  
 /**
   * If entry for this spaceId already exists, adds extractor to it.
   * Otherwise, creates a new entry with spaceId and extractor.
   */
  def addExtractor (spaceId: UUID, extractor: String) {
	  //will add extractor to the list of extractors for this space, only if it's not there.
	  val query = MongoDBObject("spaceId" -> spaceId.stringify)	  
	  ExtractorsForSpaceDAO.update(query, $addToSet("extractors" -> extractor), true, false, WriteConcern.Safe)	   
  }

  /**
   * Returns a list of extractors associated with this spaceId.
   */
  def getAllExtractors(spaceId: UUID): List[String] = {
    //Note: in models.ExtractorsForSpace, spaceId must be a String
    // if space Id is UUID, will compile but throws Box run-time error
    val query = MongoDBObject("spaceId" -> spaceId.stringify)

    val list = (for (extr <- ExtractorsForSpaceDAO.find(query)) yield extr).toList
    //get extractors' names for given space id
    val extractorList: List[String] = list.flatMap(_.extractors)
    extractorList
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
  
  object ExtractorsForSpaceDAO extends ModelCompanion[ExtractorsForSpace, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ExtractorsForSpace, ObjectId](collection = x.collection("spaces.extractors")) {}
  }
}