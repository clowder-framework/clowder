/**
 *
 */
package services.cassandra

import models.Dataset
import models.Collection
import services.DatasetService

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
  
  /**
   * 
   */
  def getFileId(datasetId: String, filename: String): Option[String] = {
    None
  }
  
  def modifyRDFOfMetadataChangedDatasets(){}
  
  def modifyRDFUserMetadata(id: String, mappingNumber: String="1") = {}
  
}