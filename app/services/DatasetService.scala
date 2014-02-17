/**
 *
 */
package services
import models._
import play.api.libs.json.JsValue
import scala.util.Try
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import models.File
import com.mongodb.casbah.WriteConcern
import play.api.Logger

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
   * Check if a dataset is in a specific collection.
   */
  def isInCollection(dataset: Dataset, collection: Collection): Boolean
  
  /**
   * Get the id of a file based on its filename and dataset it belongs to.
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

  def addMetadata(id: String, json: String)

  def addXMLMetadata(id: String, fileId: String, json: String)

  def addUserMetadata(id: String, json: String)

  /**
   * Add file to dataset.
   */
  def addFile(datasetId: String, file: File)

  /**
   * Remove file from dataset.
   */
  def removeFile(datasetId: String, fileId: String)

  /**
   * Set new thumbnail.
   */
  def createThumbnail(datasetId: String)

  /**
   * Update thumbnail used to represent this dataset.
   */
  def updateThumbnail(datasetId: String, thumbnailId: String)

  def selectNewThumbnailFromFiles(datasetId: String)

  def index(id: String)

  def removeTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def removeAllTags(id: String)

  def getUserMetadataJSON(id: String): String

  def searchUserMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset]

  def searchAllMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset]

  def removeDataset(id: String)
}