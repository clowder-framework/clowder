/**
 *
 */
package services

import models.Collection
import scala.util.Try

/**
 * Generic collection service.
 * 
 * @author Constantinos Sophocleous
 *
 */
trait CollectionService {

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
   * Get collection.
   */
  def get(id: String): Option[Collection]
  
  /**
   * 
   */
  def listInsideDataset(datasetId: String): List[Collection]
  
  /**
   * 
   */
  def listOutsideDataset(datasetId: String): List[Collection]

  /**
   * Add datataset to collection
   */
  def addDataset(collectionId: String, datasetId: String): Try[Unit]

  /**
   * Remove dataset from collection
   */
  def removeDataset(collectionId: String, datasetId: String, ignoreNotFound: String): Try[Unit]

  /**
   * Delete collection and any reference of it
   */
  def delete(collectionId: String): Try[Unit]

  def deleteAll()
}
