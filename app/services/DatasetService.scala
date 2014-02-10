/**
 *
 */
package services
import models.Dataset
import models.Collection
import play.api.libs.json.JsValue
import scala.util.Try

/**
 * Generic dataset service.
 * 
 * @author Luigi Marini
 *
 */
trait DatasetService {
  
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

  /**
   * Insert dataset.
   */
  def insert(dataset: Dataset): Option[String]
  
  /**
   * 
   */
  def listInsideCollection(collectionId: String) : List[Dataset]
  
  /**
   * 
   */
  def isInCollection(dataset: Dataset, collection: Collection): Boolean
  
  /**
   * 
   */
  def getFileId(datasetId: String, filename: String): Option[String]

  /**
   * Get JSON representation.
   */
  def toJSON(dataset: Dataset): JsValue

  /**
   * Check if dataset belongs to a collection.
   */
  def isInCollection(datasetId: String, collectionId: String): Boolean


  def modifyRDFOfMetadataChangedDatasets()
  
  def modifyRDFUserMetadata(id: String, mappingNumber: String="1")
}