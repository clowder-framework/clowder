package services

import models._
import play.api.libs.json.JsValue
import com.mongodb.casbah.Imports._
import models.File

/**
 * Generic dataset service.
 *
 * @author Luigi Marini
 *
 */
trait DatasetService {
  /**
   * The number of datasets
   */
  def count(): Long

  /**
   * Return a count of datasets in a space, this does not check for permissions
   */
  def countSpace(space: String): Long

  /**
   * Return a list of datasets in a space, this does not check for permissions
   */
  def listSpace(limit: Integer, space: String): List[Dataset]

  /**
   * Return a list of datasets in a space starting at a specific date, this does not check for permissions
   */
  def listSpace(date: String, nextPage: Boolean, limit: Integer, space: String): List[Dataset]

  /**
   * Return a count of datasets in a collection, this does not check for permissions
   */
  def countCollection(collection: String): Long

  /**
   * Return a list of datasets in a collection, this does not check for permissions
   */
  def listCollection(collection: String): List[Dataset]

  /**
   * Return a list of datasets in a collection, this does not check for permissions
   */
  def listCollection(limit: Integer, collection: String): List[Dataset]

  /**
   * Return a list of datasets in a collection starting at a specific date, this does not check for permissions
   */
  def listCollection(date: String, nextPage: Boolean, limit: Integer, collection: String): List[Dataset]

  /**
   * Return a count of datasets the user has access to.
   */
  def countAccess(user: Option[User], showAll: Boolean): Long

  /**
   * Return a list of datasets the user has access to.
   */
  def listAccess(limit: Integer, user: Option[User], showAll: Boolean): List[Dataset]

  /**
   * Return a list of datasets the user has access to.
   */
  def listAccess(limit: Integer, title: String, user: Option[User], showAll: Boolean): List[Dataset]

  /**
   * Return a list of datasets the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean): List[Dataset]

  /**
   * Return a list of datasets the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, title: String, user: Option[User], showAll: Boolean): List[Dataset]

  /**
   * Return a count of datasets the user has created.
   */
  def countUser(user: Option[User], showAll: Boolean, owner: User): Long

  /**
   * Return a list of datasets the user has created.
   */
  def listUser(limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Dataset]

  /**
   * Return a list of datasets the user has created starting at a specific date.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Dataset]

  /**
   * Get dataset.
   */
  def get(id: UUID): Option[Dataset]

  /**
   * Insert dataset.
   */
  def insert(dataset: Dataset): Option[String]

  /**
   * Check if a dataset is in a specific collection.
   */
  def isInCollection(dataset: Dataset, collection: Collection): Boolean

  /**
   * Get the id of a file based on its filename and dataset it belongs to.
   */
  def getFileId(datasetId: UUID, filename: String): Option[UUID]

  /**
   * Check if dataset belongs to a collection.
   */
  def isInCollection(datasetId: UUID, collectionId: UUID): Boolean


  def modifyRDFOfMetadataChangedDatasets()

  def dumpAllDatasetGroupings(): List[String]
  
  def dumpAllDatasetMetadata(): List[String]

  def modifyRDFUserMetadata(id: UUID, mappingNumber: String="1")

  def addMetadata(id: UUID, json: String)

  def addXMLMetadata(id: UUID, fileId: UUID, json: String)

  def addUserMetadata(id: UUID, json: String)

  /**
   * Add file to dataset.
   */
  def addFile(datasetId: UUID, file: File)

  /**
   * Remove file from dataset.
   */
  def removeFile(datasetId: UUID, fileId: UUID)

  /**
   * Set new thumbnail.
   */
  def createThumbnail(datasetId: UUID)

  /**
   * Update thumbnail used to represent this dataset.
   */
  def updateThumbnail(datasetId: UUID, thumbnailId: UUID)

  def selectNewThumbnailFromFiles(datasetId: UUID)

  def index(id: UUID)

  def removeTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def removeTag(id: UUID, tagId: UUID)

  def removeAllTags(id: UUID)

  def getUserMetadataJSON(id: UUID): String

  def searchUserMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset]

  def searchAllMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset]

  def removeDataset(id: UUID)

  def findOneByFileId(file_id: UUID): Option[Dataset]

  def findByFileId(file_id: UUID): List[Dataset]

  def findNotContainingFile(file_id: UUID): List[Dataset]

  def findByTag(tag: String): List[Dataset]

  def findByTag(tag: String, start: String, limit: Integer, reverse: Boolean): List[Dataset]

  def getMetadata(id: UUID): Map[String, Any]

  def getUserMetadata(id: UUID): scala.collection.mutable.Map[String, Any]

  def getTechnicalMetadataJSON(id: UUID): String

  def getXMLMetadataJSON(id: UUID): String

  def removeXMLMetadata(id: UUID, fileId: UUID)

  def addTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def setUserMetadataWasModified(id: UUID, wasModified: Boolean)

  def findMetadataChangedDatasets(): List[Dataset]

  /**
   * Check recursively whether a dataset's user-input metadata match a requested search tree.
   */
  def searchUserMetadata(id: UUID, requestedMetadataQuery: Any): Boolean

  def searchMetadataFormulateQuery(requestedMap: java.util.LinkedHashMap[String, Any], root: String): MongoDBObject

  /**
   * Check recursively whether a (sub)tree of a dataset's metadata matches a requested search subtree.
   */
  def searchMetadata(id: UUID, requestedMap: java.util.LinkedHashMap[String, Any], currentMap: scala.collection.mutable.Map[String, Any]): Boolean

  def addCollection(datasetId: UUID, collectionId: UUID)

  def removeCollection(datasetId: UUID, collectionId: UUID)

  def newThumbnail(datasetId: UUID)

  def update(dataset: Dataset)

  /**
   * Update the administrative information associated with the dataset. This information includes the owner, the
   * description, and the date created. Currently, only the description is editable. In the future, other items
   * or new data may be added that will be editable.
   *
   * id: The id of the dataset
   * description: A String that represents the updated information for the dataset description.
   * name: A String that represents the updated name for this dataset.
   */
  def updateInformation(id: UUID, description: String, name: String)

  def updateName(id: UUID, name: String)

  def updateDescription(id: UUID, description: String)

  /**
   * Update the license data that is currently associated with the dataset.
   *
   * id: The id of the dataset
   * licenseType: A String representing the type of license
   * rightsHolder: A String that is the free-text describing the owner of the license. Only required for certain license types
   * licenseText: Text that describes what the license is
   * licenseUrl: A reference to the license information
   * allowDownload: true or false, to allow downloading of the file or dataset. Relevant only for certain license types
   */
  def updateLicense(id: UUID, licenseType: String, rightsHolder: String, licenseText: String, licenseUrl: String, allowDownload: String)

  def setNotesHTML(id: UUID, notesHTML: String)

  /**
   * Associate a dataset with a space
   */
  def addToSpace(dataset: UUID, space: UUID)

  /**
   * Remove association between dataset and space
   */
  def removeFromSpace(dataset:UUID, space:UUID)

  /**
   * Add follower to a dataset.
   */
  def addFollower(id: UUID, userId: UUID)

  /**
   * Remove follower from a dataset.
   */
  def removeFollower(id: UUID, userId: UUID)
}

