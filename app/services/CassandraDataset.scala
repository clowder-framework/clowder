/**
 *
 */
package services

import models.Dataset
import models.Collection

/**
 * Store datasets in Cassandra.
 * 
 * @author Luigi Marini
 *
 */
class CassandraDataset extends DatasetService {
 /**
   * List all datasets in the system.
   */
  def listDatasets(): List[Dataset] = {
    List.empty[Dataset]
  }
  
  /**
   * List all datasets in the system in reverse chronological order.
   */
  def listDatasetsChronoReverse(): List[Dataset] = {
    List.empty[Dataset]
  }
  
  /**
   * List datasets after a specified date.
   */
  def listDatasetsAfter(date: String, limit: Int): List[Dataset] = {
    List.empty[Dataset]
  }
  
  /**
   * List datasets before a specified date.
   */
  def listDatasetsBefore(date: String, limit: Int): List[Dataset] = {
    List.empty[Dataset]
  }
  
  /**
   * Get dataset.
   */
  def get(id: String): Option[Dataset] = {
    None
  }
  
  /**
   * 
   */
  def listInsideCollection(collectionId: String) : List[Dataset] = {
    List.empty[Dataset]
  }
  
  /**
   * 
   */
  def isInCollection(dataset: Dataset, collection: Collection): Boolean  = {
    false
  }
}