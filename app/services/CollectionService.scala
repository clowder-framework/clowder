package services

import api.Permission.Permission
import models.{User, UUID, Dataset, Collection}
import scala.collection.mutable.ListBuffer
import scala.util.Try

/**
 * Generic collection service.
 *
 */
trait CollectionService {
  /**
   * The number of collections
   */
  def count(): Long

  /**
   * Return the count of collections in a space, this does not check for permissions
   */
  def countSpace(space: String): Long

  /**
   * Return a list of collections in a space, this does not check for permissions
   */
  def listSpace(limit: Integer, space: String): List[Collection]

  /**
   * Return a list of collections in a space starting at a specific date, this does not check for permissions
   */
  def listSpace(date: String, nextPage: Boolean, limit: Integer, space: String): List[Collection]

  /**
   * Return the count of collections the user has access to.
   */
  def countAccess(permisions: Set[Permission], user: Option[User], showAll: Boolean): Long

  /**
   * Return a list of collections the user has access to.
   */
  def listAccess(limit: Integer, permisions: Set[Permission], user: Option[User], showAll: Boolean): List[Collection]

  /**
   * Return a list of collections the user has access to.
   */
  def listAccess(limit: Integer, title: String, permisions: Set[Permission], user: Option[User], showAll: Boolean): List[Collection]

  /**
   * Return a list of collections the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, permisions: Set[Permission], user: Option[User], showAll: Boolean): List[Collection]

  /**
   * Return a list of collections the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, title: String, permisions: Set[Permission], user: Option[User], showAll: Boolean): List[Collection]

  /**
   * Return the count of collections the user has created.
   */
  def countUser(user: Option[User], showAll: Boolean, owner: User): Long

  /**
   * Return a list of collections the user has created.
   */
  def listUser(limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Collection]

  /**
   * Return a list of collections the user has created with matching title.
   */
  def listUser(limit: Integer, title: String, user: Option[User], showAll: Boolean, owner: User): List[Collection]

  /**
   * Return a list of collections the user has created starting at a specific date.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Collection]

  /**
   * Return a list of collections the user has access to starting at a specific date with matching title.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, title: String, user: Option[User], showAll: Boolean, owner: User): List[Collection]

  def updateAuthorFullName(userId: UUID, fullName: String)
  /**
   * Get collection.
   */
  def get(id: UUID): Option[Collection]

  /**
   * Create collection.
   */
  def insert(collection: Collection): Option[String]

  /**
   * Add datataset to collection
   */
  def addDataset(collectionId: UUID, datasetId: UUID): Try[Unit]

  /**
   * Remove dataset from collection
   */
  def removeDataset(collectionId: UUID, datasetId: UUID, ignoreNotFound: Boolean = true): Try[Unit]

  /**
   * Update name of the dataset
   */
  def updateName(id: UUID, name: String)

  /**
   * Update description of the dataset
   */
  def updateDescription(id: UUID, description: String)

  /**
   * Delete collection and any reference of it
   */
  def delete(collectionId: UUID): Try[Unit]

  def deleteAll()

  def findOneByDatasetId(datasetId: UUID): Option[Collection]

  /**
   * List all collections outside a dataset.
   */
  def listOutsideDataset(datasetId: UUID, user: Option[User], showAll: Boolean): List[Collection]

  /**
   * List all collections inside a dataset.
   */
  def listInsideDataset(datasetId: UUID, user: Option[User], showAll: Boolean): List[Collection]


  def isInDataset(dataset: Dataset, collection: Collection): Boolean

  /**
   * Update thumbnail used to represent this collection.
   */
  def updateThumbnail(collectionId: UUID, thumbnailId: UUID)

  /**
   * Set new thumbnail.
   */
  def createThumbnail(collectionId: UUID)

  /**
   * Add follower to a collection.
   */
  def addFollower(id: UUID, userId: UUID)

  /**
   * Remove follower from a collection.
   */
  def removeFollower(id: UUID, userId: UUID)

  /**
   * Associate a collection with a space
   */
  def addToSpace(collection: UUID, space: UUID)

  /**
   * Remove association between a collection and a space.
   */
  def removeFromSpace(collection: UUID, space: UUID)
  /**
    * Add subcollection to collection
    *
    */
  def addSubCollection(collectionId: UUID, subCollectionId: UUID) : Try[Unit]


  def getSelfAndAncestors(collectionId :UUID) : List[Collection]

  def removeSubCollection(collectionId: UUID, subCollectionId: UUID, ignoreNotFound: Boolean = true) : Try[Unit]

  def removeSubCollectionId(subCollectionId: UUID, collection: Collection,ignoreNotFound: Boolean = true) : Try[Unit]

  def removeParentCollectionId(parentCollectionId: UUID, collection: Collection, ignoreNotFound: Boolean = true) : Try[Unit]

  def listChildCollections(parentCollectionId: UUID) : List[Collection]

  def getAllDescendants(parentCollectionId : UUID) : ListBuffer[Collection]

  def getRootCollections(collectionId : UUID) : ListBuffer[Collection]

  def getRootSpaceIds(collectionId : UUID) : ListBuffer[UUID]

  def hasParentInSpace(collectionId : UUID, spaceId: UUID) : Boolean

  def hasRoot(collection: Collection): Boolean

  def addToRootSpaces(collectionId: UUID, spaceId: UUID)

  def removeFromRootSpaces(collectionId: UUID, spaceId: UUID)


}
