/**
 *
 */
package services
import models.Dataset

/**
 * Generic dataset service.
 * 
 * @author Luigi Marini
 *
 */
abstract class DatasetService {
  
  /**
   * List all datasets in the system.
   */
  def listDatasets(): List[Dataset]
  
  /**
   * List all datasets in the system in reverse chronological order.
   */
  def listDatasetsChronoReverse(): List[Dataset]
  
  /**
   * List datasets after a specified date.
   */
  def listDatasetsAfter(date: String, limit: Int): List[Dataset]
  
  /**
   * List datasets before a specified date.
   */
  def listDatasetsBefore(date: String, limit: Int): List[Dataset]
  
  /**
   * Get dataset.
   */
  def get(id: String): Option[Dataset]
}