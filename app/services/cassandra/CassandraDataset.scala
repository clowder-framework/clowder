package services.cassandra

import models.{UUID, Dataset, Collection}
import services.DatasetService
import play.api.libs.json.{JsNull, JsValue}
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import models.File

/**
 * Store datasets in Cassandra.
 * 
 * @author Luigi Marini
 *
 */
class CassandraDataset extends DatasetService {
 /**
  * Count all datasets
  */
 def count(): Long = {
  -1
 }

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
   * List datasets after a specified date.
   */
  def listUserDatasetsAfter(date: String, limit: Int, email: String): List[Dataset] = {
    List.empty[Dataset]
  }
  
  /**
   * List datasets before a specified date.
   */
  def listUserDatasetsBefore(date: String, limit: Int, email: String): List[Dataset] = {
    List.empty[Dataset]
  }
  /**
   * Get dataset.
   */
  def get(id: UUID): Option[Dataset] = {
    None
  }

  /**
   * Lastest dataset in chronological order.
   */
  def latest(): Option[Dataset] = None

  /**
   * First dataset in chronological order.
   */
  def first(): Option[Dataset] = None

  def insert(dataset: Dataset): Option[String] = None

  /**
   * 
   */
  def listInsideCollection(collectionId: UUID) : List[Dataset] = {
    List.empty[Dataset]
  }
  
  /**
   * 
   */
  def isInCollection(dataset: Dataset, collection: Collection): Boolean  = {
    false
  }

  def getFileId(datasetId: UUID, filename: String): Option[UUID] = {
    None
  }

  def toJSON(dataset: Dataset): JsValue = {
    JsNull
  }

  def isInCollection(datasetId: UUID, collectionId: UUID): Boolean = {
    return false
  }
  
  def modifyRDFOfMetadataChangedDatasets(){}
  
  def modifyRDFUserMetadata(id: UUID, mappingNumber: String="1") {}

  def addMetadata(id: UUID, json: String) {}

  def addXMLMetadata(id: UUID, fileId: UUID, json: String) {}

  def addUserMetadata(id: UUID, json: String) {}

  def addFile(datasetId: UUID, file: File) {}

  def removeFile(datasetId: UUID, fileId: UUID) {}

  def createThumbnail(datasetId: UUID) {}

  def updateThumbnail(datasetId: UUID, thumbnailId: UUID) {}

  def selectNewThumbnailFromFiles(datasetId: UUID) {}

  def index(id: UUID) {}

  def removeTag(id: UUID, tagId: UUID) {}

  def removeTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String]) {}

  def removeAllTags(id: UUID) {}

  def searchUserMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset] = {List.empty}

  def searchAllMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset] = {List.empty}

  def removeDataset(id: UUID) {}

  def getUserMetadataJSON(id: UUID): String = ""

  def findOneByFileId(file_id: UUID) = None

  def findByFileId(file_id: UUID) = List.empty

  def findNotContainingFile(file_id: UUID) = List.empty

  def findByTag(tag: String) = List.empty

  def findByTag(tag: String, start: String, limit: Integer, reverse: Boolean) = List.empty

  def getMetadata(id: UUID) = Map.empty

  def getUserMetadata(id: UUID) = scala.collection.mutable.Map.empty

  def getTechnicalMetadataJSON(id: UUID) = ""

  def getXMLMetadataJSON(id: UUID) = ""

  def removeXMLMetadata(id: UUID, fileId: UUID) {}

  /**
   * Implementation of updateInformation defined in services/DatasetService.scala.
   */
  def updateInformation(id: UUID, description: String, name: String) {}

  /**
   * Implementation of updateLicenseing defined in services/DatasetService.scala.
   */
  def updateLicense(id: UUID, licenseType: String, rightsHolder: String, licenseText: String, licenseUrl: String, allowDownload: String) {}
  
  def addTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String]) {}

  def setUserMetadataWasModified(id: UUID, wasModified: Boolean) {}

  def findMetadataChangedDatasets() = List.empty

  /**
   * Check recursively whether a dataset's user-input metadata match a requested search tree.
   */
  def searchUserMetadata(id: UUID, requestedMetadataQuery: Any) = false

  def searchMetadataFormulateQuery(requestedMap: java.util.LinkedHashMap[String, Any], root: String) = MongoDBObject()

  /**
   * Check recursively whether a (sub)tree of a dataset's metadata matches a requested search subtree.
   */
  def searchMetadata(id: UUID, requestedMap: java.util.LinkedHashMap[String, Any], currentMap: scala.collection.mutable.Map[String, Any]) = false

  def addCollection(datasetId: UUID, collectionId: UUID) {}

  def removeCollection(datasetId: UUID, collectionId: UUID) {}

  def newThumbnail(datasetId: UUID) {}

  def update(dataset: Dataset) {}
  
  def setNotesHTML(id: UUID, notesHTML: String) {}

  def dumpAllDatasetGroupings(): List[String] = {List.empty}
  
  def dumpAllDatasetMetadata(): List[String] = {List.empty}

 /**
  * Add follower to a dataset.
  */
 def addFollower(id: UUID, userId: UUID) {}

 /**
  * Remove follower from a dataset.
  */
 def removeFollower(id: UUID, userId: UUID) {}
}
