package services

import models.{UUID, Dataset, Collection}
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
   * List collections in the system.
   */
  def listCollections(order: Option[String] = None, limit: Option[Integer] = None, space: Option[String] = None): List[Collection]

  /**
   * List collections in the system in reverse chronological order.
   */
  def listCollectionsChronoReverse(limit: Option[Integer] = None, space: Option[String] = None): List[Collection]
  
  /**
   * List collections after a specified date.
   */
  def listCollectionsAfter(date: String, limit: Int, space: Option[String] = None): List[Collection]
  
  /**
   * List collections before a specified date.
   */
  def listCollectionsBefore(date: String, limit: Int, space: Option[String] = None): List[Collection]
  
  /**
   * List collections for a specific user after a specified date.
   */
  def listUserCollectionsAfter(date: String, limit: Int, email: String) : List[Collection]
  
  /**
   * List collections for a specific user before a specified date.
   */
  def listUserCollectionsBefore(date: String, limit: Int, email: String) : List[Collection]
  
  /**
   * Get collection.
   */
  def get(id: UUID): Option[Collection]

  /**
   * Lastest collection in chronological order.
   */
  def latest(space: Option[String] = None): Option[Collection]

  /**
   * First collection in chronological order.
   */
  def first(space: Option[String] = None): Option[Collection]

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
  def listOutsideDataset(datasetId: UUID): List[Collection]

  /**
   * List all collections inside a dataset.
   */
  def listInsideDataset(datasetId: UUID): List[Collection]


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
