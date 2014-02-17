/**
 *
 */
package services.cassandra

import models.{File, Dataset, Collection}
import services.DatasetService
import play.api.libs.json.{JsNull, JsValue}
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import models.File
import com.mongodb.casbah.WriteConcern

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

  def insert(dataset: Dataset): Option[String] = None

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

  def getFileId(datasetId: String, filename: String): Option[String] = {
    None
  }

  def toJSON(dataset: Dataset): JsValue = {
    JsNull
  }

  def isInCollection(datasetId: String, collectionId: String): Boolean = {
    return false
  }
  
  def modifyRDFOfMetadataChangedDatasets(){}
  
  def modifyRDFUserMetadata(id: String, mappingNumber: String="1") {}

  def addMetadata(id: String, json: String) {}

  def addXMLMetadata(id: String, fileId: String, json: String) {}

  def addUserMetadata(id: String, json: String) {}

  def addFile(datasetId: String, file: File) {}

  def removeFile(datasetId: String, fileId: String) {}

  def createThumbnail(datasetId: String) {}

  def updateThumbnail(datasetId: String, thumbnailId: String) {}

  def selectNewThumbnailFromFiles(datasetId: String) {}

  def index(id: String) {}

  def removeTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String]) {}

  def removeAllTags(id: String) {}

  def searchUserMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset] = {List.empty}

  def searchAllMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset] = {List.empty}

  def removeDataset(id: String) {}

  def getUserMetadataJSON(id: String): String = ""
}