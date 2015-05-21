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
   * List all collections in the system.
   */
  def listCollections(space: Option[String] = None): List[Collection]
  
  /**
   * List collections that belong to a specific space. Empty list is returned if there
   * are none that apply.
   * 
   * @param spaceId The identifier for the space to be checked
   * 
   * @return A List of Collection objects that are assigned to the specified Space.
   * 
   */
  def listCollectionsBySpace(spaceId: UUID): List[Collection]

  /**
   * List all collections in the system in reverse chronological order.
   */
  def listCollectionsChronoReverse(space: Option[String] = None): List[Collection]
  
  /**
   * List collections after a specified date.
   */
  def listCollectionsAfter(date: String, limit: Int, space: Option[String] = None): List[Collection]
  
  /**
   * List collections before a specified date.
   */
  def listCollectionsBefore(date: String, limit: Int, space: Option[String] = None): List[Collection]
  
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
   * Associate a collection with a space
   */
  def addToSpace(collection: UUID, space: UUID)
}
