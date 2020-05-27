package services.mongodb


import javax.inject.{Inject, Singleton}
import api.Permission
import api.Permission.Permission
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.DBObject
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models._
import org.bson.types.ObjectId
import play.api.Logger
import play.api.i18n.Messages
import play.{Logger => log}
import play.api.Play._
import securesocial.controllers.Registration
import securesocial.controllers.Registration._
import securesocial.core.providers.utils.RoutesHelper
import services._
import MongoContext.context
import models.Collection
import models.Dataset
import models.Role
import models.User
import util.Formatters
/**
 * Store Spaces in MongoDB.
 *
 */
@Singleton
class MongoDBSpaceService @Inject() (
  collections: CollectionService,
  files: FileService,
  datasets: DatasetService,
  users: UserService,
  curations: CurationService,
  metadatas: MetadataService,
  events: EventService) extends SpaceService {

  def get(id: UUID): Option[ProjectSpace] = {
    ProjectSpaceDAO.findOneById(new ObjectId(id.stringify))
  }

  def get(ids: List[UUID]): DBResult[ProjectSpace] = {
    val objectIdList = ids.map(id => new ObjectId(id.stringify))

    val query = MongoDBObject("_id" -> MongoDBObject("$in" -> objectIdList))
    val found = ProjectSpaceDAO.find(query).toList
    val notFound = ids.diff(found.map(_.id))

    if (notFound.length > 0)
      Logger.error("Not all space IDs found for bulk get request")
    return DBResult(found, notFound)
  }

  /** count all spaces */
  def count(): Long = {
    ProjectSpaceDAO.count(MongoDBObject())  }

  /** list all spaces */
  def list(): List[ProjectSpace] = {
    list(None, nextPage=false, 0, None, Set[Permission](Permission.ViewSpace), None, showAll=true, None, true, false,false)
  }

  /**
   * Count all spaces the user has access to.
   */
  def countAccess(permissions: Set[Permission], user: Option[User], showAll: Boolean): Long = {
    count(None, nextPage=false, permissions, user, showAll, None)
  }

  /**
   * Return a list of spaces the user has access to.
   */
  def listAccess(limit: Integer, permissions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean,  onlyTrial: Boolean, showOnlyShared : Boolean): List[ProjectSpace] = {
    list(None, nextPage=false, limit, None, permissions, user, showAll, None, showPublic, onlyTrial, showOnlyShared)
  }

  /**
   * Return a list of spaces the user has access to.
   */
  def listAccess(limit: Integer, title:String, permissions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean): List[ProjectSpace] = {
    list(None, nextPage=false, limit, Some(title), permissions, user, showAll, None, showPublic, false, showOnlyShared)
  }

  /**
   * Return a list of spaces the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, permissions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean,  onlyTrial: Boolean, showOnlyShared : Boolean): List[ProjectSpace] = {
    list(Some(date), nextPage, limit, None, permissions, user, showAll, None, showPublic, onlyTrial, showOnlyShared)
  }

  /**
   * Return a list of spaces the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, title: String, permissions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean): List[ProjectSpace] = {
    list(Some(date), nextPage, limit, Some(title), permissions, user, showAll, None, showPublic, false, showOnlyShared)
  }

  /**
   * Count all spaces the user has created.
   */
  def countUser(user: Option[User], showAll: Boolean, owner: User): Long = {
    count(None, nextPage=false, Set[Permission](Permission.ViewSpace), user, showAll, Some(owner))
  }

  /**
   * Return a list of spaces the user has created.
   */
  def listUser(limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[ProjectSpace] = {
    list(None, nextPage=false, limit, None, Set[Permission](Permission.ViewSpace), user, showAll, Some(owner), true, false, false)
  }

  /**
   * Return a list of spaces the user has created with matching title.
   */
  def listUser(limit: Integer, title: String, user: Option[User], showAll: Boolean, owner: User): List[ProjectSpace] = {
    list(None, nextPage=false, limit, Some(title), Set[Permission](Permission.ViewSpace), user, showAll, Some(owner), true, false, false)
  }

  /**
   * Return a list of spaces the user has created starting at a specific date.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[ProjectSpace] = {
    list(Some(date), nextPage, limit, None, Set[Permission](Permission.ViewSpace), user, showAll, Some(owner), false, false, false)
  }

  /**
   * Return a list of spaces the user has created starting at a specific date with matching title.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, title: String, user: Option[User], showAll: Boolean, owner: User): List[ProjectSpace] = {
    list(Some(date), nextPage, limit, Some(title), Set[Permission](Permission.ViewSpace), user, showAll, Some(owner), false, false, false)
  }

  def listByStatus(status: String):List[ProjectSpace] = {
    ProjectSpaceDAO.find(MongoDBObject("status" -> status)).toList
  }

  /**
   * return count based on input
   */
  private def count(date: Option[String], nextPage: Boolean, permissions: Set[Permission], user: Option[User], showAll: Boolean, owner: Option[User]): Long = {
    val (filter, _) = filteredQuery(date, nextPage, None, permissions, user, showAll, owner, true, false, false)
    ProjectSpaceDAO.count(filter)
  }


  /**
   * return list based on input
   */
  private def list(date: Option[String], nextPage: Boolean, limit: Integer, title: Option[String], permissions: Set[Permission], user: Option[User], showAll: Boolean, owner: Option[User], showPublic: Boolean = true, onlyTrial: Boolean = false, showOnlyShared : Boolean): List[ProjectSpace] = {
    val (filter, sort) = filteredQuery(date, nextPage, title, permissions, user, showAll, owner, showPublic, onlyTrial, showOnlyShared)
    if (date.isEmpty || nextPage) {
      ProjectSpaceDAO.find(filter).sort(sort).limit(limit).toList
    } else {
      ProjectSpaceDAO.find(filter).sort(sort).limit(limit).toList.reverse
    }
  }

  /**
   * Monster function, does all the work. Will create a filters and sorts based on the given parameters
   */
  private def filteredQuery(date: Option[String], nextPage: Boolean, titleSearch: Option[String], permissions: Set[Permission], user: Option[User], showAll: Boolean, owner: Option[User], showPublic: Boolean, onlyTrial: Boolean, showOnlyShared : Boolean): (DBObject, DBObject) = {
    // filter =
    // - owner   == show datasets owned by owner that user can see
    // - space   == show all datasets in space
    // - access  == show all datasets the user can see
    // - default == public only
    val verifySpaces = play.Play.application().configuration().getBoolean("verifySpaces")
    val statusFilter = if(onlyTrial) {
      MongoDBObject("status" -> SpaceStatus.TRIAL.toString)
    } else if(verifySpaces){
      MongoDBObject("status" -> MongoDBObject("$in" ->  List(SpaceStatus.PUBLIC.toString, SpaceStatus.PRIVATE.toString)))
    } else if(showPublic){
      MongoDBObject("status" -> SpaceStatus.PUBLIC.toString)
    } else {
      MongoDBObject()
    }


    val filter = owner match {
      case Some(o) => {
        val author = MongoDBObject("creator" -> new ObjectId(o.id.stringify))
        if (showAll) {
          author
        } else {
          user match {
            case Some(u) => {
              if(showPublic) {
                author ++ $or(statusFilter, ("_id" $in u.spaceandrole.filter(_.role.permissions.intersect(permissions.map(_.toString)).nonEmpty).map(x => new ObjectId(x.spaceId.stringify))))
              } else {
                author ++ $or(("_id" $in u.spaceandrole.filter(_.role.permissions.intersect(permissions.map(_.toString)).nonEmpty).map(x => new ObjectId(x.spaceId.stringify))))
              }

            }
            case None => {
              if(showPublic) {
                author ++ statusFilter
              } else {
                author
              }

            }
          }
        }
      }
      case None => {
        if (showAll && showPublic && !verifySpaces && configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public") {
          MongoDBObject()
        } else {
          user match {
            case Some(u) => {
              val author = $and(MongoDBObject("author.identityId.userId" -> u.identityId.userId) ++ MongoDBObject("author.identityId.providerId" -> u.identityId.providerId))
              if (onlyTrial) {
                statusFilter
              }
              else if (u.superAdminMode) {
                MongoDBObject()
              } else if (permissions.contains(Permission.ViewSpace) && play.Play.application().configuration().getBoolean("enablePublic") && showPublic && showOnlyShared) {
                $or(author, statusFilter, ("_id" $in u.spaceandrole.filter(_.role.permissions.intersect(permissions.map(_.toString)).nonEmpty).filter((p: UserSpaceAndRole) =>
                  get(p.spaceId) match {
                    case Some(space) => {
                      if (space.userCount > 1) {
                        true
                      } else {
                        false
                      }
                    }
                    case None => false
                  }
                ).map(x => new ObjectId(x.spaceId.stringify))))
              } else if (permissions.contains(Permission.ViewSpace) && play.Play.application().configuration().getBoolean("enablePublic") && showPublic) {
                $or(author, statusFilter,  ("_id" $in u.spaceandrole.filter(_.role.permissions.intersect(permissions.map(_.toString)).nonEmpty).map(x => new ObjectId(x.spaceId.stringify))))
              } else {
                $or(author, ("_id" $in u.spaceandrole.filter(_.role.permissions.intersect(permissions.map(_.toString)).nonEmpty).map(x => new ObjectId(x.spaceId.stringify))))

              }
            }
            case None => if(showPublic) {
             statusFilter
            } else {
              MongoDBObject("doesnotexist" -> true)
            }
          }
        }
      }
    }
    val filterTitle = titleSearch match {
      case Some(title) =>  MongoDBObject("name" -> ("(?i)" + title).r)
      case None => MongoDBObject()
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

    val filterNotShared = if (showOnlyShared && owner.isEmpty){
      MongoDBObject("userCount" -> MongoDBObject("$not" -> MongoDBObject("$size" -> 0)))
    } else {
      MongoDBObject()
    }

    val sort = if (date.isDefined && !nextPage) {
      MongoDBObject("created"-> 1) ++ MongoDBObject("name" -> 1)
    } else {
      MongoDBObject("created" -> -1) ++ MongoDBObject("name" -> 1)
    }

    (filter ++ filterTitle ++ filterDate ++ filterNotShared, sort)
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
    // Although we current don't use this function to update space's name, this part is added for consistency
    get(space.id) match {
      case Some(s) => {
        if (space.name != s.name) {
          events.updateObjectName(space.id, space.name)
        }
      }
      case None =>
    }
    ProjectSpaceDAO.save(space)
  }

  def delete(id: UUID, host: String, apiKey: Option[String], user: Option[User]): Unit = {
    // only curation objects in this space are removed, since dataset & collection don't need to belong to a space.
    get(id) match {
      case Some(s) => {
        s.curationObjects.map(c => curations.remove(c, host, apiKey, user))
        for(follower <- s.followers) {
          users.unfollowResource(follower, ResourceRef(ResourceRef.space, id))
        }
        //Remove all users from the space.
        val spaceUsers = getUsersInSpace(id)
        for(usr <- spaceUsers){
          removeUser(usr.id, id)
        }
        metadatas.removeDefinitionsBySpace(id)
      }
      case None =>
    }
    ProjectSpaceDAO.removeById(new ObjectId(id.stringify))
  }

  /**
   * Associate a collection with a space
   *
   * @param collection collection id
   * @param space space id
   */
  def addCollection(collection: UUID, space: UUID, user : Option[User]): Unit = {
    log.debug(s"Adding $collection to $space")

    collections.addToSpace(collection, space)
    collections.get(collection) match {
      case Some(current_collection) => {

        if (play.Play.application().configuration().getBoolean("addDatasetToCollectionSpace")){
          val datasetsInCollection = datasets.listCollection(current_collection.id.stringify, user)
          for (dataset <- datasetsInCollection){
            if (!dataset.spaces.contains(space)){
              addDataset(dataset.id,space)
            }
          }
        }

        collections.get(current_collection.child_collection_ids).found.foreach(child_collection => {
          if (!child_collection.spaces.contains(space)){
            addCollection(child_collection.id, space, user)
          }
          collections.syncUpRootSpaces(child_collection.id, child_collection.root_spaces)
        })
      } case None => {
        log.error("No collection found for " + collection)
      }
    }
  }

  def incrementCollectionCounter(collenction:UUID, space: UUID, increment: Int ): Unit = {
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(space.stringify)), $inc("collectionCount" -> increment), upsert=false, multi=false, WriteConcern.Safe)
  }

  def decrementCollectionCounter(collection: UUID, space: UUID, decrement: Int): Unit = {
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(space.stringify)), $inc("collectionCount" -> -1), upsert=false, multi=false, WriteConcern.Safe)
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
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(space.stringify)), $inc("datasetCount" -> 1), upsert=false, multi=false, WriteConcern.Safe)

  }

  /**
   * Remove association betweren dataset and a space
    *
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
  def purgeExpiredResources(space: UUID, host: String, apiKey: Option[String], user: Option[User]): Unit = {
      val datasetsList = getDatasetsInSpace(Some(space.stringify))
      val collectionsList = getCollectionsInSpace(Some(space.stringify))
      val timeToLive = getTimeToLive(space)
      val currentTime = System.currentTimeMillis()

      for (aDataset <- datasetsList) {
    	  val datasetTime = aDataset.lastModifiedDate.getTime()
    	  val difference = currentTime - datasetTime
    	  if (difference > timeToLive) {
    	       //It was last modified longer than the time to live, so remove it.
    	       datasets.removeDataset(aDataset.id, host, apiKey, user)
    	  }
      }

      for (aCollection <- collectionsList) {
          val collectionTime = aCollection.lastModifiedDate.getTime()
          val difference = currentTime - collectionTime
          if (difference > timeToLive) {
              //It was last modified longer than the time to live, so remiove it.
              for (colDataset <- datasets.listCollection(aCollection.id.stringify)) {
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
                      case _ =>
                    }
                  }
                }
                datasetOnlyInSpace match {
                  case Some(true) => {
                    //If the dataset only exists in the current space, it can be removed.
                    datasets.removeDataset(colDataset.id, host, apiKey, user)
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
  def updateSpaceConfiguration(spaceId: UUID, name: String, description: String, timeToLive: Long, expireEnabled: Boolean, access:String) {
    get(spaceId) match {
      case Some(s) if name != s.name => {
        events.updateObjectName(spaceId, name)
      }
      case _ =>
    }
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(spaceId.stringify)),
      $set("description" -> description, "name" -> name, "resourceTimeToLive" -> timeToLive, "isTimeToLiveEnabled" -> expireEnabled, "status" -> access),
      false, false, WriteConcern.Safe)
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def addUser(userId: UUID, role: Role, spaceId: UUID): Unit = {
    users.addUserToSpace(userId, role, spaceId)
    removeRequest(spaceId, userId)
    ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(spaceId.stringify)), $inc("userCount" -> 1), upsert=false, multi=false, WriteConcern.Safe)
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
      val retList = users.listUsersInSpace(spaceId)
      retList
  }

  /**
   * @see app.services.SpaceService.scala
   *
   * Implementation of the SpaceService trait.
   *
   */
  def getRoleForUserInSpace(spaceId: UUID, userId: UUID): Option[Role] = {
      val retRole = users.getUserRoleInSpace(userId, spaceId)
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

  def processInvitation(email: String) = {
    getInvitationByEmail(email).map { invite =>
      users.findByEmail(invite.email) match {
        case Some(user) => {
          users.findRole(invite.role) match {
            case Some(role) => {
              addUser(user.id, role, invite.space)
              removeInvitationFromSpace(UUID(invite.invite_id), invite.space)
            }
            case None => Logger.error(email+" could not be added to space (missing role "+invite.role+")")
          }
        }
        case None => Logger.error("No user found with email "+email)
      }
    }
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
