package services.cassandra

import api.UserRequest
import models._
import services.DatasetService
import play.api.libs.json.{JsNull, JsValue}
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._

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
  def count(space: Option[String]): Long = -1

 /**
  * Return a list of datasets in a space, this does not check for permissions
  */
 def listSpace(limit: Integer, space: String): List[Dataset] = List.empty[Dataset]

 /**
  * Return a list of datasets in a space starting at a specific date, this does not check for permissions
  */
 def listSpace(date: String, nextPage: Boolean, limit: Integer, space: String): List[Dataset] = List.empty[Dataset]

 /**
  * Return a list of datasets the user has access to.
  */
 def listAccess(limit: Integer, user: Option[User], superAdmin: Boolean): List[Dataset] = List.empty[Dataset]

 /**
  * Return a list of datasets the user has access to starting at a specific date.
  */
 def listAccess(date: String, nextPage: Boolean, limit: Integer, user: Option[User], superAdmin: Boolean): List[Dataset] = List.empty[Dataset]

 /**
  * Return a list of datasets the user has created.
  */
 def listUser(limit: Integer, user: Option[User], superAdmin: Boolean, owner: User): List[Dataset] = List.empty[Dataset]

 /**
  * Return a list of datasets the user has created starting at a specific date.
  */
 def listUser(date: String, nextPage: Boolean, limit: Integer, user: Option[User], superAdmin: Boolean, owner: User): List[Dataset] = List.empty[Dataset]

 /**
   * Get dataset.
   */
  def get(id: UUID): Option[Dataset] = {
    None
  }

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

  def addToSpace(dataset: UUID, space: UUID) {}

 def removeFromSpace(dataset: UUID, space: UUID) {}
 /**
  * Add follower to a dataset.
  */
 def addFollower(id: UUID, userId: UUID) {}

 /**
  * Remove follower from a dataset.
  */
 def removeFollower(id: UUID, userId: UUID) {}
}
