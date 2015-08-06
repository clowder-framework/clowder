package services

import models.{User, UUID, Dataset, Collection}
import scala.util.Try

/**
 * Generic collection service.
 * 
 * @author Constantinos Sophocleous
 *
 */
trait CollectionService {
  /**
   * The number of collections
   */
  def count(space: Option[String] = None): Long

  /**
   * Return a list of datasets in a space, this does not check for permissions
   */
  def listSpace(limit: Integer, space: String): List[Collection]

  /**
   * Return a list of datasets in a space starting at a specific date, this does not check for permissions
   */
  def listSpace(date: String, nextPage: Boolean, limit: Integer, space: String): List[Collection]

  /**
   * Return a list of datasets the user has access to.
   */
  def listAccess(limit: Integer, user: Option[User], superAdmin: Boolean): List[Collection]

  /**
   * Return a list of datasets the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, user: Option[User], superAdmin: Boolean): List[Collection]

  /**
   * Return a list of datasets the user has created.
   */
  def listUser(limit: Integer, user: Option[User], superAdmin: Boolean, owner: User): List[Collection]

  /**
   * Return a list of datasets the user has created starting at a specific date.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, user: Option[User], superAdmin: Boolean, owner: User): List[Collection]

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
   * Delete collection and any reference of it
   */
  def delete(collectionId: UUID): Try[Unit]

  def deleteAll()

  def findOneByDatasetId(datasetId: UUID): Option[Collection]

  /**
   * List all collections outside a dataset.
   */
  def listOutsideDataset(datasetId: UUID, user: Option[User], superAdmin: Boolean): List[Collection]

  /**
   * List all collections inside a dataset.
   */
  def listInsideDataset(datasetId: UUID, user: Option[User], superAdmin: Boolean): List[Collection]


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
}
