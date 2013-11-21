/**
 *
 */
package services

import models.Collection

/**
 * Generic collection service.
 * 
 * @author Constantinos Sophocleous
 *
 */
abstract class CollectionService {

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
}
