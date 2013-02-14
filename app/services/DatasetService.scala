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
   * Get dataset.
   */
  def get(id: String): Option[Dataset]
}