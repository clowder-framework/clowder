/**
 *
 */
package services.mongodb

import com.mongodb.casbah.WriteConcern
import models.{User, UUID, Collection, Dataset}
import com.mongodb.casbah.commons.MongoDBObject
import java.text.SimpleDateFormat
import org.bson.types.ObjectId
import play.api.Logger
import util.Formatters
import scala.util.Try
import services._
import javax.inject.{Singleton, Inject}
import scala.util.Failure
import scala.Some
import scala.util.Success
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.Imports._
import MongoContext.context
import play.api.Play.current


/**
 * Use Mongodb to store collections.
 * 
 * @author Constantinos Sophocleous
 *
 */
@Singleton
class MongoDBCollectionService @Inject() (datasets: DatasetService, userService: UserService)  extends CollectionService {
  /**
   * Count all collections
   */
  def count(): Long = {
    Collection.count( MongoDBObject())
  }

  /**
   * Return the count of collections in a space, this does not check for permissions
   */
  def countSpace(space: String): Long = {
    count(None, false, Some(space), None, superAdmin=false, None)
  }

  /**
   * Return a list of collections in a space, this does not check for permissions
   */
  def listSpace(limit: Integer, space: String): List[Collection] = {
    list(None, false, limit, Some(space), None, superAdmin=false, None)
  }

  /**
   * Return a list of collections in a space starting at a specific date, this does not check for permissions
   */
  def listSpace(date: String, nextPage: Boolean, limit: Integer, space: String): List[Collection] = {
    list(Some(date), nextPage, limit, Some(space), None, superAdmin=false, None)
  }

  /**
   * Return the count of collections the user has access to.
   */
  def countAccess(user: Option[User], superAdmin: Boolean): Long = {
    count(None, false, None, user, superAdmin, None)
  }

  /**
   * Return a list of collections the user has access to.
   */
  def listAccess(limit: Integer, user: Option[User], superAdmin: Boolean): List[Collection] = {
    list(None, false, limit, None, user, superAdmin, None)
  }

  /**
   * Return a list of collections the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, user: Option[User], superAdmin: Boolean): List[Collection] = {
    list(Some(date), nextPage, limit, None, user, superAdmin, None)
  }

  /**
   * Return the count of collections the user has created.
   */
  def countUser(user: Option[User], superAdmin: Boolean, owner: User): Long = {
    count(None, false, None, user, superAdmin, Some(owner))
  }

  /**
   * Return a list of collections the user has created.
   */
  def listUser(limit: Integer, user: Option[User], superAdmin: Boolean, owner: User): List[Collection] = {
    list(None, false, limit, None, user, superAdmin, Some(owner))
  }

  /**
   * Return a list of collections the user has created starting at a specific date.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, user: Option[User], superAdmin: Boolean, owner: User): List[Collection] = {
    list(Some(date), nextPage, limit, None, user, superAdmin, Some(owner))
  }

  /**
   * Return count of the requested collections
   */
  private def count(date: Option[String], nextPage: Boolean, space: Option[String], user: Option[User], superAdmin: Boolean, owner: Option[User]): Long = {
    val (filter, _) = filteredQuery(date, nextPage, space, user, superAdmin, owner)
    Collection.count(filter)
  }

  /**
   * Return a list of the requested collections
   */
  private def list(date: Option[String], nextPage: Boolean, limit: Integer, space: Option[String], user: Option[User], superAdmin: Boolean, owner: Option[User]): List[Collection] = {
    val (filter, sort) = filteredQuery(date, nextPage, space, user, superAdmin, owner)
    if (date.isEmpty || nextPage) {
      Collection.find(filter).sort(sort).limit(limit).toList
    } else {
      Collection.find(filter).sort(sort).limit(limit).toList.reverse
    }
  }

  /**
   * Monster function, does all the work. Will create a filters and sorts based on the given parameters
   */
  private def filteredQuery(date: Option[String], nextPage: Boolean, space: Option[String], user: Option[User], superAdmin: Boolean, owner: Option[User]):(DBObject, DBObject) = {
    // filter =
    // - owner   == show collections owned by owner that user can see
    // - space   == show all collections in space
    // - access  == show all collections the user can see
    // - default == public only
    val public = MongoDBObject("public" -> true)
    val emptySpaces = MongoDBObject("spaces" -> List.empty)
    val filter = owner match {
      case Some(o) => {
        val author = MongoDBObject("author.identityId.userId" -> o.identityId.userId) ++ MongoDBObject("author.identityId.providerId" -> o.identityId.providerId)
        user match {
          case Some(u) => {
            if (superAdmin) {
              author
            } else {
              if (u == o) {
                author ++ $or(public, emptySpaces, ("spaces" $in u.spaceandrole.map(x => new ObjectId(x.spaceId.stringify))))
              } else {
                author ++ $or(public, ("spaces" $in u.spaceandrole.map(x => new ObjectId(x.spaceId.stringify))))
              }
            }
          }
          case None => {
            author ++ public
          }
        }
      }
      case None => {
        space match {
          case Some(s) => MongoDBObject("spaces" -> new ObjectId(s))
          case None => {
            user match {
              case Some(u) => {
                val author = $and(MongoDBObject("author.identityId.userId" -> u.identityId.userId) ++ MongoDBObject("author.identityId.providerId" -> u.identityId.providerId))
                $or(author, public, ("spaces" $in u.spaceandrole.map(x => new ObjectId(x.spaceId.stringify))))
              }
              case None => public
            }
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
      Logger.info("After " + sinceDate)
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
      Logger.info("Before " + sinceDate)
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
      collectionList= collectionList.filter(x=> x.author.get.email.toString == "Some(" +email +")")
      collectionList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.info("After " + sinceDate)
      var collectionList = Collection.find("created" $lt sinceDate).sort(order).limit(limit).toList
      collectionList= collectionList.filter(x=> x.author.get.email.toString == "Some(" +email +")")
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
      collectionList= collectionList.filter(x=> x.author.get.email.toString == "Some(" +email +")")
      collectionList
    } else {
      order = MongoDBObject("created"-> 1)
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.info("Before " + sinceDate)
      var collectionList = Collection.find("created" $gt sinceDate).sort(order).limit(limit + 1).toList.reverse
      collectionList = collectionList.filter(_ != collectionList.last)
      collectionList= collectionList.filter(x=> x.author.get.email.toString == "Some(" +email +")")
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
  def listOutsideDataset(datasetId: UUID, user: Option[User], superAdmin: Boolean): List[Collection] = {
    Dataset.findOneById(new ObjectId(datasetId.stringify)) match {
      case Some(dataset) => {
        val list = for (collection <- listAccess(0, user, superAdmin); if (!isInDataset(dataset, collection))) yield collection
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
  def listInsideDataset(datasetId: UUID, user: Option[User], superAdmin: Boolean): List[Collection] = {
    Dataset.findOneById(new ObjectId(datasetId.stringify)) match {
      case Some(dataset) => {
        val list = for (collection <- listAccess(0, user, superAdmin); if (isInDataset(dataset, collection))) yield collection
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
              
              if(!collection.thumbnail_id.isEmpty && !dataset.thumbnail_id.isEmpty){
	        	  if(collection.thumbnail_id.get == dataset.thumbnail_id.get){
	        		  createThumbnail(collection.id)
	        	  }		                        
	          }

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
        for (follower <- collection.followers) {
          userService.unfollowCollection(follower, collectionId)
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
     val result = Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)),
     $set("name" -> name), false, false, WriteConcern.Safe)
   }

  def updateDescription(collectionId: UUID, description: String){
    val result = Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId.stringify)),
      $set("description" -> description), false, false, WriteConcern.Safe)
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
}

object Collection extends ModelCompanion[Collection, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Collection, ObjectId](collection = x.collection("collections")) {}
  }
}
