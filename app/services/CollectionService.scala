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
  def count(): Long

  /**
   * List all collections in the system.
   */
  def listCollections(): List[Collection]

  /**
   * List all collections in the system in reverse chronological order.
   */
  def listCollectionsChronoReverse(): List[Collection]
  
  /**
   * List collections after a specified date.
   */
  def listCollectionsAfter(date: String, limit: Int): List[Collection]
  
  /**
   * List collections before a specified date.
   */
  def listCollectionsBefore(date: String, limit: Int): List[Collection]
  
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
  def latest(): Option[Collection]

  /**
   * First collection in chronological order.
   */
  def first(): Option[Collection]

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

}
