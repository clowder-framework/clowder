/**
 *
 */
package services.mongodb

import api.Permission
import api.Permission.Permission
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import models._
import com.mongodb.casbah.commons.MongoDBObject
import java.text.SimpleDateFormat
import org.bson.types.ObjectId
import play.api.Logger
import util.{Formatters, SearchUtils}
import scala.collection.mutable.ListBuffer
import scala.util.Try
import services._
import javax.inject.{Singleton, Inject}
import scala.util.Failure
import scala.util.Success
import MongoContext.context
import play.api.Play._


/**
 * Use Mongodb to store collections.
 *
 *
 */
@Singleton
class MongoDBCollectionService @Inject() (
  datasets: DatasetService,
  userService: UserService,
  spaceService: SpaceService,
  events:EventService,
  spaces:SpaceService )  extends CollectionService {
  /**
   * Count all collections
   */
  def count(): Long = {
    Collection.count( MongoDBObject())
  }

  /**
   * Return the count of root collections in a space, this does not check for permissions
   */
  def countSpace(space: String): Long = {
    count(None, false,  None, Some(space), Set[Permission](Permission.ViewCollection), None, showAll=true, None, false)
  }

  /**
   * Return a list of collections in a space, this does not check for permissions
   */
  def listSpace(limit: Integer, space: String): List[Collection] = {
    list(None, false, limit, None, Some(space), Set[Permission](Permission.ViewCollection), None, showAll=true, owner = None)
  }

  /**
    * Return a list of collections in a space and checks for permissions
    */
  def listInSpaceList(title: Option[String], date: Option[String], limit: Integer, spaces: List[UUID], permissions: Set[Permission], user: Option[User], exactMatch : Boolean = false): List[Collection] = {
    val (filter, sort) = filteredQuery(date, false, title, None, permissions, user, true, None, true, false, exactMatch)
    Collection.find(filter ++  ("spaces" $in spaces.map(x => new ObjectId(x.stringify)))).limit(limit).toList
  }

  /**
   * Return a list of collections in a space starting at a specific date, this does not check for permissions
   */
  def listSpace(date: String, nextPage: Boolean, limit: Integer, space: String): List[Collection] = {
    list(Some(date), nextPage, limit, None, Some(space), Set[Permission](Permission.ViewCollection), None, showAll=true, owner=None)
  }

  /**
   * Return the count of collections the user has access to.
   */
  def countAccess(permissions: Set[Permission], user: Option[User], showAll: Boolean): Long = {
    count(None, false,  None, None, permissions, user, showAll, None)
  }

  /**
   * Return a list of collections the user has access to.
   */
  def listAccess(limit: Integer, permissions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean): List[Collection] = {
    list(None, false, limit, None, None, permissions, user, showAll, None, showPublic, showOnlyShared)
  }

  /**
   * Return a list of collections the user has access to.
   */
  def listAccess(limit: Integer, title: String, permissions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean, exact: Boolean): List[Collection] = {
    list(None, false, limit, Some(title), None, permissions, user, showAll, None, showPublic, showOnlyShared, exactMatch=exact)
  }

  /**
   * Return a list of collections the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, permissions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean): List[Collection] = {
    list(Some(date), nextPage, limit, None, None, permissions, user, showAll, None, showPublic, showOnlyShared)
  }

  /**
   * Return a list of collections the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, title: String, permissions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean, exact: Boolean): List[Collection] = {
    list(Some(date), nextPage, limit, Some(title), None, permissions, user, showAll, None, showPublic, showOnlyShared, exactMatch=exact)
  }

  /**
   * Return the count of collections the user has created.
   */
  def countUser(user: Option[User], showAll: Boolean, owner: User): Long = {
    count(None, false, None, None, Set[Permission](Permission.ViewCollection), user, showAll, Some(owner))
  }

  /**
   * Return a list of collections the user has created.
   */
  def listUser(limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Collection] = {
    list(None, false, limit, None, None, Set[Permission](Permission.ViewCollection), user, showAll, Some(owner))
  }

  /**
   * Return a list of collections the user has created with matching title.
   */
  def listUser(limit: Integer, title: String, user: Option[User], showAll: Boolean, owner: User, exact: Boolean): List[Collection] = {
    list(None, false, limit, Some(title), None, Set[Permission](Permission.ViewCollection), user, showAll, Some(owner), exactMatch=exact)
  }

  /**
   * Return a list of collections the user has created starting at a specific date.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Collection] = {
    list(Some(date), nextPage, limit, None, None, Set[Permission](Permission.ViewCollection), user, showAll, Some(owner))
  }

  /**
   * Return a list of collections the user has created starting at a specific date with matching title.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, title: String, user: Option[User], showAll: Boolean, owner: User, exact: Boolean): List[Collection] = {
    list(Some(date), nextPage, limit, Some(title), None, Set[Permission](Permission.ViewCollection), user, showAll, Some(owner), exactMatch=exact)
  }
  
  def listSpaceAccess(limit: Integer, space: String, permissions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean) = {
    list(None, false, 0, None, Option(space), permissions, user, showAll, None, showPublic)
  }

  /**
   * Return count of the requested collections
   */
  private def count(date: Option[String], nextPage: Boolean, title: Option[String], space: Option[String], permissions: Set[Permission], user: Option[User],
                    showAll: Boolean, owner: Option[User], exactMatch : Boolean = false): Long = {
    val (filter, _) = filteredQuery(date, nextPage, title, space, Set[Permission](Permission.ViewCollection), user, showAll, owner, true, false, exactMatch)
    Collection.count(filter)
  }

  /**
   * Return a list of the requested collections
   */
  private def list(date: Option[String], nextPage: Boolean, limit: Integer, title: Option[String], space: Option[String], permissions: Set[Permission], user: Option[User],
                   showAll: Boolean, owner: Option[User], showPublic: Boolean = true, showOnlyShared : Boolean = false, exactMatch : Boolean = false): List[Collection] = {
    val (filter, sort) = filteredQuery(date, nextPage, title, space, permissions, user, showAll, owner, showPublic, showOnlyShared, exactMatch)
    if (date.isEmpty || nextPage) {
      Collection.find(filter).sort(sort).limit(limit).toList
    } else {
      Collection.find(filter).sort(sort).limit(limit).toList.reverse
    }
  }

  /**
   * Monster function, does all the work. Will create a filters and sorts based on the given parameters
   */
  private def filteredQuery(date: Option[String], nextPage: Boolean, titleSearch: Option[String], space: Option[String], permissions: Set[Permission], user: Option[User],
                            showAll: Boolean, owner: Option[User], showPublic: Boolean, showOnlyShared : Boolean, exactMatch : Boolean):(DBObject, DBObject) = {

    // In /Collections page you should see:
    //  a) Parent Collections in a space you belong to ( root_collections.length > 0 and you belong to the space).
    //  b) Parent Collections that you created (owner = you and root_collections.length = 0).
    // You should not see:
    //  a) Child collections of the spaces you belong to (root_collections.length == 0)
    //  b) Child collections of parents that are not in a space and you created
    //  c) Parent or child collections in spaces you don’t have access to.
    //Within a space page you should see:
    //  Parent collections within that space. (root_spaces includes spaceId)
    //On the home page you should see parent and child collections you have created wether or not they are part of a space
    //On the dropdown in the dataset page ‘Add dataset to collection’ you should see parent and child collections you have access to via a space or that you created.

    // create access filter

    val publicSpaces= spaces.listByStatus(SpaceStatus.PUBLIC.toString).map(s => new ObjectId(s.id.stringify))
    val enablePublic = play.Play.application().configuration().getBoolean("enablePublic")
//    val rootQuery = $or(("root_spaces" $exists true), ("parent_collection_ids" $exists false))
    val filterAccess = if (showAll || (configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public" && permissions.contains(Permission.ViewCollection))) {
      MongoDBObject()
    } else {
      user match {
        case Some(u) => {
          val orlist = collection.mutable.ListBuffer.empty[MongoDBObject]
          if (permissions.contains(Permission.ViewCollection) && enablePublic && showAll) {
            orlist += ("spaces" $in publicSpaces)
          }
          if(user == owner || owner.isEmpty) {
            if (owner.isEmpty && !showOnlyShared){
              orlist += MongoDBObject("author._id" -> new ObjectId(u.id.stringify))
            } else if (!owner.isEmpty){
              orlist += MongoDBObject("author._id" -> new ObjectId(u.id.stringify))
            }
          }
          val permissionsString = permissions.map(_.toString)
          val okspaces = if (showOnlyShared){
            u.spaceandrole.filter(_.role.permissions.intersect(permissionsString).nonEmpty).filter((p: UserSpaceAndRole)=>
              (spaces.get(p.spaceId) match {
                case Some(space) => {
                  if (space.userCount > 1){
                    true
                  } else {
                    false
                  }
                }
                case None => false
              })
            )
          } else {
            u.spaceandrole.filter(_.role.permissions.intersect(permissionsString).nonEmpty)
          }
          if (okspaces.nonEmpty) {
            orlist += ("spaces" $in okspaces.map(x => new ObjectId(x.spaceId.stringify)))
          }
          if (orlist.isEmpty) {
            orlist += MongoDBObject("doesnotexist" -> true)
          }
          $or(orlist.map(_.asDBObject))
        }
        case None => {
          if(enablePublic && showPublic) {
            MongoDBObject("root_spaces" -> MongoDBObject("$not" -> MongoDBObject("$size" -> 0))) ++ ("spaces" $in publicSpaces)
          } else {
            MongoDBObject("root_spaces" -> MongoDBObject("$not" -> MongoDBObject("$size" -> 0)))
          }

        }
      }
    }
    val filterOwner = owner match {
      case Some(o) =>  MongoDBObject("author._id" -> new ObjectId(o.id.stringify))
      case None => {
        space match {
          case Some(s) => MongoDBObject()
          case None => {
            if (permissions.contains(Permission.AddResourceToCollection)) {
              MongoDBObject()
            }
            else if (showAll || (configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public" && permissions.contains(Permission.ViewCollection))) {
              val orlist = collection.mutable.ListBuffer.empty[MongoDBObject]
                orlist += MongoDBObject("parent_collection_ids" -> List.empty)
                orlist += MongoDBObject("root_spaces" -> MongoDBObject("$not" -> MongoDBObject("$size" -> 0)))
              $or(orlist.map(_.asDBObject))
            } else {
              user match {
                case Some(u) => {
                  val orlist = collection.mutable.ListBuffer.empty[MongoDBObject]
                  orlist += MongoDBObject("spaces" -> List.empty) ++ MongoDBObject("author._id" -> new ObjectId(u.id.stringify)) ++ MongoDBObject("parent_collection_ids" -> List.empty)
                  val permissionsString = permissions.map(_.toString)
                  val orlistB = collection.mutable.ListBuffer.empty[MongoDBObject]

                  val okspaces = u.spaceandrole.filter(_.role.permissions.intersect(permissionsString).nonEmpty)
                  if (okspaces.nonEmpty) {
                    if(enablePublic && showPublic) {
                      orlistB += ("spaces" $in okspaces.map(x => new ObjectId(x.spaceId.stringify)) ++ publicSpaces)
                    } else {
                      orlistB += ("spaces" $in okspaces.map(x => new ObjectId(x.spaceId.stringify)))
                    }
                    orlist += (MongoDBObject("root_spaces" -> MongoDBObject("$not" -> MongoDBObject("$size" -> 0))) ++ $or(orlistB.map(_.asDBObject)))
                  } else if(enablePublic && showPublic && publicSpaces.nonEmpty) {
                    orlistB += ("spaces" $in publicSpaces)
                    orlist += (MongoDBObject("root_spaces" -> MongoDBObject("$not" -> MongoDBObject("$size" -> 0))) ++ $or(orlistB.map(_.asDBObject)))
                  }
                  $or(orlist.map(_.asDBObject))

                }
                case None => {
                  if(enablePublic && showPublic) {
                    ("spaces" $in publicSpaces)
                  } else {
                    MongoDBObject("doesnotexist" -> true)
                  }

                }
              }
            }

          }
        }
      }
    }
    val filterSpace = space match {
      case Some(s) => MongoDBObject("root_spaces" -> new ObjectId(s))
      case None => MongoDBObject()
    }
    val filterTitle = titleSearch match {
      case Some(title) =>
        if (exactMatch) {
          MongoDBObject("name" -> title)
        } else {
          MongoDBObject("name" -> ("(?i)" + title).r)
        }
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
      case None => MongoDBObject()
    }

    val filterNotShared = if (showOnlyShared && owner.isEmpty){
      MongoDBObject("spaces" -> MongoDBObject("$not" -> MongoDBObject("$size" -> 0)))
    } else {
      MongoDBObject()
    }

    val sort = if (date.isDefined && !nextPage) {
      MongoDBObject("created"-> 1) ++ MongoDBObject("name" -> 1)
    } else {
      MongoDBObject("created" -> -1) ++ MongoDBObject("name" -> 1)
    }

    (filterAccess ++ filterDate ++ filterTitle ++ filterSpace ++ filterOwner ++ filterNotShared
      , sort)
  }

  def listAllCollections(user: User, showAll: Boolean, limit: Int): List[Collection] ={
    if(showAll) {
      Collection.find(MongoDBObject()).limit(limit).toList
    } else {
      val publicSpaces= spaces.listByStatus(SpaceStatus.PUBLIC.toString).map(s => new ObjectId(s.id.stringify))
      val orlist = collection.mutable.ListBuffer.empty[MongoDBObject]
      orlist += ("spaces" $in publicSpaces)
      orlist += MongoDBObject("author._id" -> new ObjectId(user.id.stringify))
      val permissionsString = Set[Permission](Permission.ViewCollection).map(_.toString)
      val okspaces = user.spaceandrole.filter(_.role.permissions.intersect(permissionsString).nonEmpty)
      if (okspaces.nonEmpty) {
        orlist += ("spaces" $in okspaces.map(x => new ObjectId(x.spaceId.stringify)))
      }
      if (orlist.isEmpty) {
        orlist += MongoDBObject("doesnotexist" -> true)
      }

      Collection.find($or(orlist.map(_.asDBObject))).limit(limit).toList
    }
  }

  /**
   * List collections in the system.
   */
  def listCollections(order: Option[String], limit: Option[Integer], space: Option[String]): List[Collection] = {
    if (order.exists(_.equals("desc"))) { return listCollectionsChronoReverse(limit, space) }

    val filter = space match {
      case Some(s) => MongoDBObject("spaces" -> new ObjectId(s))
      case None => MongoDBObject()
    }
    limit match {
      case Some(l) => Collection.find(filter).limit(l).toList
      case None => Collection.find(filter).toList
    }
  }

  /**
   * List collections in the system in reverse chronological order.
   */
  def listCollectionsChronoReverse(limit: Option[Integer], space: Option[String]): List[Collection] = {
    val order = MongoDBObject("created" -> -1)
    val filter = space match {
      case Some(s) => MongoDBObject("spaces" -> new ObjectId(s))
      case None => MongoDBObject()
    }
    limit match {
      case Some(l) => Collection.find(filter).sort(order).limit(l).toList
      case None => Collection.find(filter).sort(order).toList
    }
  }

  /**
   * List collections after a specified date.
   */
  def listCollectionsAfter(date: String, limit: Int, space: Option[String]): List[Collection] = {
    val order = MongoDBObject("created" -> -1)
    val filter = space match {
      case Some(s) => MongoDBObject("spaces" -> new ObjectId(s))
      case None => MongoDBObject()
    }
    if (date == "") {
    	Collection.find(filter).sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(date)
      Logger.debug("After " + sinceDate)
      Collection.find(filter ++ ("created" $lt sinceDate)).sort(order).limit(limit).toList
    }
  }

  /**
   * List collections before a specified date.
   */
  def listCollectionsBefore(date: String, limit: Int, space: Option[String]): List[Collection] = {
    var order = MongoDBObject("created" -> -1)
    val filter = space match {
      case Some(s) => MongoDBObject("spaces" -> new ObjectId(s))
      case None => MongoDBObject()
    }
    if (date == "") {
    	Collection.find(filter).sort(order).limit(limit).toList
    } else {
      order = MongoDBObject("created" -> 1)
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(date)
      Logger.debug("Before " + sinceDate)
      Collection.find(filter ++ ("created" $gt sinceDate)).sort(order).limit(limit).toList.reverse
    }
  }

  /**
   * List collections for a specific user after a date.
   */
  def listUserCollectionsAfter(date: String, limit: Int, email: String): List[Collection] = {
    val order = MongoDBObject("created"-> -1)
    if (date == "") {
      var collectionList = Collection.findAll.sort(order).limit(limit).toList
      collectionList= collectionList.filter(x=> x.author.email.toString == "Some(" +email +")")
      collectionList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.debug("After " + sinceDate)
      var collectionList = Collection.find("created" $lt sinceDate).sort(order).limit(limit).toList
      collectionList= collectionList.filter(x=> x.author.email.toString == "Some(" +email +")")
      collectionList
    }
  }

  /**
   * List collections for a specific user before a date.
   */
  def listUserCollectionsBefore(date: String, limit: Int, email: String): List[Collection] = {
    var order = MongoDBObject("created"-> -1)
    if (date == "") {
      var collectionList = Collection.findAll.sort(order).limit(limit).toList
      collectionList= collectionList.filter(x=> x.author.email.toString == "Some(" +email +")")
      collectionList
    } else {
      order = MongoDBObject("created"-> 1)
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.debug("Before " + sinceDate)
      var collectionList = Collection.find("created" $gt sinceDate).sort(order).limit(limit + 1).toList.reverse
      collectionList = collectionList.filter(_ != collectionList.last)
      collectionList= collectionList.filter(x=> x.author.email.toString == "Some(" +email +")")
      collectionList
    }
  }

  /**
   * Get collection.
   */
  def get(id: UUID): Option[Collection] = {
    Collection.findOneById(new ObjectId(id.stringify))
  }

  def insert(collection: Collection): Option[String] = {
    Collection.insert(collection).map(_.toString)
  }

  /**
   * List all collections outside a dataset.
   */
  def listOutsideDataset(datasetId: UUID, user: Option[User], showAll: Boolean): List[Collection] = {
    Dataset.findOneById(new ObjectId(datasetId.stringify)) match {
      case Some(dataset) => {
        val list = for (collection <- listAccess(0, Set[Permission](Permission.ViewCollection), user, showAll, false, false); if (!isInDataset(dataset, collection))) yield collection
        return list.reverse
      }
      case None => {
        Logger.debug(s"Dataset $datasetId not found")
        List.empty
      }
    }
  }

  /**
   * List all collections inside a dataset.
   */
  def listInsideDataset(datasetId: UUID, user: Option[User], showAll: Boolean): List[Collection] = {
    Dataset.findOneById(new ObjectId(datasetId.stringify)) match {
      case Some(dataset) => {
        val list = for (collection <- listAccess(0, Set[Permission](Permission.ViewCollection, Permission.AddResourceToCollection), user, showAll, false, false); if (isInDataset(dataset, collection))) yield collection
        return list.reverse
      }
      case None => {
        Logger.debug(s"Dataset $datasetId not found")
        List.empty
      }
    }
  }

  def isInDataset(dataset: Dataset, collection: Collection): Boolean = {
    for (dsColls <- dataset.collections  ) {
      if (dsColls.stringify == collection.id.stringify)
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
            if(!dataset.collections.contains(collection.id.stringify)) {
              // add dataset to collection
              Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)), $inc("datasetCount" -> 1), upsert=false, multi=false, WriteConcern.Safe)
              //add collection to dataset
              datasets.addCollection(dataset.id, collection.id)
              datasets.index(dataset.id)
              index(collection.id)

              if(collection.thumbnail_id.isEmpty && !dataset.thumbnail_id.isEmpty){
                  Collection.dao.collection.update(MongoDBObject("_id" -> new ObjectId(collection.id.stringify)),
                  $set("thumbnail_id" -> dataset.thumbnail_id.get), false, false, WriteConcern.Safe)
              }

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

  //adds datasest to the spaces of the collection
  def addDatasetToCollectionSpaces(collectionId: UUID, datasetId: UUID, user : Option[User]) = Try {
    Logger.debug(s"Adding dataset $datasetId to spaces of collection $collectionId")
    Collection.findOneById(new ObjectId(collectionId.stringify)) match {
      case Some(collection) => {
        datasets.get(datasetId) match {
          case Some(dataset) => {
            for (collectionSpace <- collection.spaces) {
              spaceService.get(collectionSpace) match {
                case Some(space) => {
                  if (!dataset.spaces.contains(space.id)) {
                    //check permission for space
                    if (Permission.checkPermission(user, Permission.AddResourceToSpace,ResourceRef(ResourceRef.space, space.id))){
                      spaceService.addDataset(datasetId, collectionSpace)
                    } else
                      Logger.debug("User does not have permission to add datasets to space " + space.id)
                  }
                }
                case None => Logger.error("No space found for : " + collectionSpace)
              }
            }
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

  def addDatasetsInCollectionAndChildCollectionsToCollectionSpaces(collectionId : UUID, user : Option[User]) = Try {
    addDatasetsToCollectionSpaces(collectionId,user)
    val allDescendants = getAllDescendants(collectionId)
    for (collection <- allDescendants){
      addDatasetsToCollectionSpaces(collection.id, user)
    }
  }

  private def addDatasetsToCollectionSpaces(collectionId : UUID, user : Option[User]) = Try {
    Collection.findOneById(new ObjectId(collectionId.stringify)) match {
      case Some(collection) => {
        val datasetsInCollection = datasets.listCollection(collection.id.stringify, user)
        for (dataset <- datasetsInCollection){
          for (space <- collection.spaces){
            if (!dataset.spaces.contains(space)){
              spaceService.addDataset(dataset.id,space)
            }
          }
        }
      }
      case None => Logger.error("Error getting collection" + collectionId)
    }
  }

  def listChildCollections(parentCollectionId : UUID): List[Collection] = {
    val childCollections = List.empty[Collection]
    get(parentCollectionId) match {
      case Some(collection) => {
        val childCollectionIds = collection.child_collection_ids
        val childList = for (childCollectionId <- childCollectionIds; if (get(childCollectionId).isDefined)) yield (get(childCollectionId)).get
        return childList
      }
      case None => return childCollections
    }
  }

  def getRootCollections(collectionId : UUID) : ListBuffer[Collection] = {
    var rootCollections = ListBuffer.empty[Collection]
    Collection.findOneById(new ObjectId(collectionId.stringify)) match {
      case Some(collection) => {
        val parentCollectionIds = collection.parent_collection_ids
        for (parentCollectionId <- parentCollectionIds){
          Collection.findOneById(new ObjectId(parentCollectionId.stringify))  match {
            case Some(parentCollection) => {
              if (hasRoot(parentCollection)) {
                rootCollections += parentCollection
              } else {
                rootCollections = rootCollections++( getRootCollections(parentCollectionId))
              }
            }
            case None => Logger.error("no parent collection")
          }
        }
      }
    }
    return rootCollections
  }

  def hasRoot(collection: Collection): Boolean = {
    collection.root_spaces.length > 0
  }

  def addToRootSpaces(collectionId: UUID, spaceId: UUID): Unit = {
    Collection.update(
      MongoDBObject("_id" -> new ObjectId(collectionId.stringify)),
      $addToSet("root_spaces" -> Some(new ObjectId(spaceId.stringify))),
      false, false)
    spaceService.incrementCollectionCounter(collectionId, spaceId, 1)
  }

  def removeFromRootSpaces(collectionId: UUID, spaceId: UUID)= {
    Collection.update(
      MongoDBObject("_id" -> new ObjectId(collectionId.stringify)),
      $pull("root_spaces" -> Some(new ObjectId(spaceId.stringify))),
      false, false)
    spaceService.decrementCollectionCounter(collectionId, spaceId, 1)
  }

  def syncUpRootSpaces(collectionId: UUID, initialRootSpaces: List[UUID]): Unit ={
    get(collectionId) match {
      case Some(collection) => {
        val parentSpaces = ListBuffer.empty[UUID]
        collection.parent_collection_ids.foreach{ parentId =>
          get(parentId) match {
            case Some(parent) => parent.spaces.foreach{ space => parentSpaces += space}
            case None =>
          }
        }
        collection.spaces.foreach { space =>
          if(!(parentSpaces contains space)) {
            if(!(initialRootSpaces contains space)) {
              addToRootSpaces(collection.id, space)
            }
          } else {
            if(collection.root_spaces contains space) {
              removeFromRootSpaces(collection.id, space)
            }
          }

        }
      }
      case None =>
    }

  }

  def getRootSpaceIds(collectionId: UUID) : ListBuffer[UUID] = {
    var rootSpaceIds = ListBuffer.empty[UUID]

    val rootCollectionIds =  getRootCollections(collectionId).toList

    for (rootCollectionId <- rootCollectionIds){
      Collection.findOneById(new ObjectId(rootCollectionId.id.stringify)) match {
        case Some(rootCollection) => {
          var currentRootSpaces = rootCollection.spaces
          rootSpaceIds = rootSpaceIds ++ currentRootSpaces
        }
        case None => Logger.error("no root collection found with id " + rootCollectionId)
      }
    }

    return rootSpaceIds
  }


  def getAllDescendants(parentCollectionId : UUID) : ListBuffer[models.Collection] = {
    var descendants = ListBuffer.empty[models.Collection]

    Collection.findOneById(new ObjectId(parentCollectionId.stringify)) match {
      case Some(parentCollection) => {
        val childCollections = listChildCollections(parentCollectionId)
        for (child <- childCollections){
          descendants += child
          val otherDescendants = getAllDescendants(child.id)
          descendants = descendants ++ otherDescendants

        }

      }
      case None => Logger.error("no collection found for id " + parentCollectionId)
    }

    return descendants
  }


  def removeDataset(collectionId: UUID, datasetId: UUID, ignoreNotFound: Boolean = true) = Try {
    Logger.debug(s"Removing dataset $datasetId from collection $collectionId")
	  Collection.findOneById(new ObjectId(collectionId.stringify)) match{
      case Some(collection) => {
        datasets.get(datasetId) match {
          case Some(dataset) => {
            if(dataset.collections.contains(collection.id)){
              // remove dataset from collection
              Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)), $inc("datasetCount" -> -1), upsert=false, multi=false, WriteConcern.Safe)
              //remove collection from dataset
              datasets.removeCollection(dataset.id, collection.id)
              datasets.index(dataset.id)
              index(collection.id)

              if(!collection.thumbnail_id.isEmpty && !dataset.thumbnail_id.isEmpty){
	        	  if(collection.thumbnail_id.get == dataset.thumbnail_id.get){
	        		  createThumbnail(collection.id)
	        	  }
	          }

              Logger.debug("Removing dataset from collection completed")
            }
            else{
              Logger.debug("Dataset was already out of the collection.")
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

  def delete(collectionId: UUID) = Try {
	  Collection.findOneById(new ObjectId(collectionId.stringify)) match {
      case Some(collection) => {
        for(dataset <- datasets.listCollection(collectionId.stringify)) {
          //remove collection from dataset
          datasets.removeCollection(dataset.id, collection.id)
          datasets.index(dataset.id)
        }

        for(space <- collection.spaces){
          spaceService.removeCollection(collection.id,space)
        }

        for(space <- collection.root_spaces) {
          spaceService.decrementCollectionCounter(collectionId, space, 1)
        }

        for(follower <- collection.followers) {
          userService.unfollowCollection(follower, collectionId)
        }
        for(subCollection <- collection.child_collection_ids) {
          removeSubCollection(collectionId, subCollection)
        }

        for(parentCollection <- collection.parent_collection_ids) {
          removeSubCollection(parentCollection, collection.id)
        }

        Collection.remove(MongoDBObject("_id" -> new ObjectId(collection.id.stringify)))

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
	    		val selecteddatasets = datasets.listCollection(collectionId.stringify) map { ds =>{
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

  def index(id: Option[UUID]) = {
    id match {
      case Some(collectionId) => index(collectionId)
      case None => Collection.dao.find(MongoDBObject()).foreach(c => index(c.id))
    }
  }

  def index(id: UUID) {
    Collection.findOneById(new ObjectId(id.stringify)) match {
      case Some(collection) => {
        current.plugin[ElasticsearchPlugin].foreach {
          _.index(collection, false)
        }
      }
      case None => Logger.error("Collection not found: " + id.stringify)
    }
  }

  def addToSpace(collectionId: UUID, spaceId: UUID): Unit = {
      val result = Collection.update(
        MongoDBObject("_id" -> new ObjectId(collectionId.stringify)),
        $addToSet("spaces" -> Some(new ObjectId(spaceId.stringify))),
        false, false)
  }

  def removeFromSpace(collectionId: UUID, spaceId: UUID): Unit = {
    val result = Collection.update(
    MongoDBObject("_id" -> new ObjectId(collectionId.stringify)),
    $pull("spaces" -> Some(new ObjectId(spaceId.stringify))),
    false, false)

  }

   def updateName(collectionId: UUID, name: String){
     events.updateObjectName(collectionId, name)
     val result = Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)),
     $set("name" -> name), false, false, WriteConcern.Safe)
   }

  def updateDescription(collectionId: UUID, description: String){
    val result = Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)),
      $set("description" -> description), false, false, WriteConcern.Safe)
  }

  def updateAuthorFullName(userId: UUID, fullName: String) {
    Collection.update(MongoDBObject("author._id" -> new ObjectId(userId.stringify)),
      $set("author.fullName" -> fullName), false, true, WriteConcern.Safe)
  }

  /**
   * Add follower to a collection.
   */
  def addFollower(id: UUID, userId: UUID) {
    Collection.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
                      $addToSet("followers" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)
  }

  /**
   * Remove follower from a collection.
   */
  def removeFollower(id: UUID, userId: UUID) {
    Collection.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
                      $pull("followers" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)
  }

  def removeSubCollection(collectionId: UUID, subCollectionId: UUID, ignoreNotFound: Boolean = true) = Try {
    Collection.findOneById(new ObjectId(collectionId.stringify)) match{
      case Some(collection) => {
        Collection.findOneById(new ObjectId(subCollectionId.stringify)) match {
          case Some(sub_collection) => {
            if(isSubCollectionIdInCollection(subCollectionId,collection)){
              // remove sub collection from list of child collection
              Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)), $pull("child_collection_ids" -> Some(new ObjectId(subCollectionId.stringify))), false, false, WriteConcern.Safe)
              Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)), $inc("childCollectionsCount" -> -1), upsert=false, multi=false, WriteConcern.Safe)
              //remove collection from the list of parent collection for sub collection
              Collection.update(MongoDBObject("_id" -> new ObjectId(subCollectionId.stringify)), $pull("parent_collection_ids" -> Some(new ObjectId(collectionId.stringify))), false, false, WriteConcern.Safe)
              //Check if any of the remaining spaces come from a parent or not. If not, add it to the root_spaces
              syncUpRootSpaces(sub_collection.id, sub_collection.root_spaces)

              Logger.debug("Removing subcollection from collection completed")
            }
            else{
              Logger.debug("Subcollection was already out of the collection.")
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

  def addSubCollection(collectionId :UUID, subCollectionId: UUID, user : Option[User]) = Try{
    Collection.findOneById(new ObjectId(collectionId.stringify)) match {
      case Some(collection) => {
        get(subCollectionId) match {
          case Some(sub_collection) => {
            if (!isSubCollectionIdInCollection(subCollectionId,collection)){
              addSubCollectionId(subCollectionId,collection)
              addParentCollectionId(subCollectionId,collectionId)
              val parentSpaceIds = collection.spaces
              for (parentSpaceId <- parentSpaceIds) {
                if (!sub_collection.spaces.contains(parentSpaceId)) {
                  spaceService.get(parentSpaceId) match {
                    case Some(parentSpace) => {
                      spaceService.addCollection(subCollectionId, parentSpaceId, user)
                    }
                    case None => Logger.error("No space found for " + parentSpaceId)
                  }
                }
              }
              syncUpRootSpaces(sub_collection.id, sub_collection.root_spaces)
              index(collection.id)
              Collection.findOneById(new ObjectId(subCollectionId.stringify)) match {
                case Some(sub_collection) => {
                  index(sub_collection.id)
                } case None =>
                  Logger.error("Error getting subcollection" + subCollectionId)
                  Failure
              }
            }
          }
          case None => Logger.error("Error getting subcollection")
        }
      } case None => {
        Logger.error("Error getting collection" + collectionId)
        Failure
      }
    }
  }

  def addSubCollectionId(subCollectionId: UUID, collection: Collection) = Try {
    Collection.update(MongoDBObject("_id" -> new ObjectId((collection.id).stringify)), $addToSet("child_collection_ids" -> Some(new ObjectId(subCollectionId.stringify))), false, false, WriteConcern.Safe)
    Collection.update(MongoDBObject("_id" -> new ObjectId(collection.id.stringify)), $inc("childCollectionsCount" -> 1), upsert=false, multi=false, WriteConcern.Safe)
  }

  def removeSubCollectionId(subCollectionId: UUID, collection : Collection,ignoreNotFound: Boolean = true) = Try {
    Collection.update(MongoDBObject("_id" -> new ObjectId(collection.id.stringify)),
      $pull("child_collection_ids" -> Some(new ObjectId(subCollectionId.stringify))),
      false, false)
    Collection.update(MongoDBObject("_id" -> new ObjectId(collection.id.stringify)), $inc("childCollectionsCount" -> -1), upsert=false, multi=false, WriteConcern.Safe)

  }

  def removeParentCollectionId(parentCollectionId: UUID, collection : Collection,ignoreNotFound: Boolean = true) = Try {
    Collection.update(MongoDBObject("_id" -> new ObjectId((collection.id).stringify)),
      $pull("parent_collection_ids" -> Some(new ObjectId(parentCollectionId.stringify))),
      false, false)
    Collection.update(MongoDBObject("_id" -> new ObjectId(parentCollectionId.stringify)), $inc("childCollectionsCount" -> -1), upsert=false, multi=false, WriteConcern.Safe)

  }

  def addParentCollectionId(subCollectionId: UUID, parentCollectionId: UUID) = Try {
    Collection.update(MongoDBObject("_id" -> new ObjectId(subCollectionId.stringify)), $addToSet("parent_collection_ids" -> Some(new ObjectId(parentCollectionId.stringify))), false, false, WriteConcern.Safe)
  }


  def getSelfAndAncestors(collectionId : UUID) : List[Collection] = {
    var selfAndAncestors : ListBuffer[models.Collection] = ListBuffer.empty[models.Collection]
    get(collectionId) match {
      case Some(collection) => {
        selfAndAncestors += collection
        for (parentCollectionId <- collection.parent_collection_ids){
          get(parentCollectionId) match {
            case Some(parent_collection) => {
              selfAndAncestors = selfAndAncestors ++ getSelfAndAncestors(parentCollectionId)
            }
            case None =>
          }
        }
      }
      case None =>
    }
    return selfAndAncestors.toList

  }

  def hasParentInSpace(collectionId : UUID, spaceId: UUID) : Boolean = {
    get(collectionId) match {
      case Some(collection) => {
        spaceService.get(spaceId) match {
          case Some(space) => {
            for (parentId <- collection.parent_collection_ids) {
              get(parentId) match {
                case Some(parentCollection) => {
                  if (parentCollection.spaces.contains(spaceId)) {
                    return true
                  }
                }
                case None => return false
              }
            }
          }
          case None =>
        }
      }
      case None =>
    }
    return false
  }

  private def isSubCollectionIdInCollection(subCollectionId: UUID, collection: Collection) : Boolean = {
    if (collection.child_collection_ids.contains(subCollectionId)){
      return true
    }
    else{
      return false
    }
  }
}

object Collection extends ModelCompanion[Collection, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Collection, ObjectId](collection = x.collection("collections")) {}
  }
}
