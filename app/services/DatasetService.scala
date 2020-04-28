package services

import java.util.Date

import api.Permission.Permission
import com.mongodb.casbah.Imports._
import models.{File, _}

/**
 * Generic dataset service.
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
    * Return a list of datasets in a space
    */
  def listSpace(limit: Integer, space: String, user:Option[User]): List[Dataset]

  /**
    * Return a list of datasets in a space starting at a specific date
    */
  def listSpace(date: String, nextPage: Boolean, limit: Integer, space: String, user:Option[User]): List[Dataset]

  /**
    * Return a list of datasets in a space filtered by status, this does not check for permissions
    */
  def listSpaceStatus(limit: Integer, space: String, status: String): List[Dataset]
  /**
    * Return a list of datasets in a space filtered by status
    */
  def listSpaceStatus(limit: Integer, space: String, status: String, user:Option[User]): List[Dataset]
  /**
    * Return a list of datasets in a space filtered by status
    */
  def listSpaceStatus(date: String, nextPage: Boolean, limit: Integer, space: String, status: String, user:Option[User]): List[Dataset]

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
    * Return a list of datasets in a collection
    */
  def listCollection(collection: String, user:Option[User]): List[Dataset]

  /**
    * Return a list of datasets in a collection
    */
  def listCollection(limit: Integer, collection: String, user:Option[User]): List[Dataset]

  /**
    * Return a list of datasets in a collection starting at a specific date
    */
  def listCollection(date: String, nextPage: Boolean, limit: Integer, collection: String, user:Option[User]): List[Dataset]

  /**
   * Return a count of datasets the user has access to.
   */
  def countAccess(permisions: Set[Permission], user: Option[User], showAll: Boolean): Long

  /**
   * Return a list of datasets the user has access to.
   */
  def listAccess(limit: Integer, permisions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean): List[Dataset]

  /**
   * Return a list of datasets the user has access to.
   */
  def listAccess(limit: Integer, title: String, permisions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean, exact: Boolean): List[Dataset]

  /**
   * Return a list of datasets the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, permisions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean): List[Dataset]

  /**
   * Return a list of datasets the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, title: String, permisions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean, exact: Boolean): List[Dataset]

  /**
    * Return a list of datasets in a space the user has access to.
    */
  def listSpaceAccess(limit: Integer, permisions: Set[Permission], space: String, user: Option[User], showAll: Boolean, showPublic: Boolean): List[Dataset]

  /**
    * Return a list of datasets in a space with specific title the user has access to.
    */
  def listSpaceAccess(limit: Integer, title: String, permisions: Set[Permission], space: String, user: Option[User], showAll: Boolean, showPublic: Boolean): List[Dataset]

  /**
    * Return a list of datasets  in a space the user has access to starting at a specific date.
    */
  def listSpaceAccess(date: String, nextPage: Boolean, limit: Integer, permisions: Set[Permission], space: String, user: Option[User], showAll: Boolean, showPublic: Boolean): List[Dataset]

  /**
    * Return a list of datasets in a space the user has access to starting at a specific date with specific title.
    */
  def listSpaceAccess(date: String, nextPage: Boolean, limit: Integer, title: String, permisions: Set[Permission], space: String, user: Option[User], showAll: Boolean, showPublic: Boolean): List[Dataset]

  /**
   * Return a count of datasets the user has created.
   */
  def countUser(user: Option[User], showAll: Boolean, owner: User): Long

  /**
   * Return a list of datasets the user has created.
   */
  def listUser(limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Dataset]

  /**
    * Return a list of datasets the user has created in trash.
    */
  def listUserTrash(limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Dataset]

  /**
   * Return a list of datasets the user has created starting at a specific date.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Dataset]

  /**
    * Return a list of datasets the user has created starting at a specific date.
    */
  def listUserTrash(date: String, nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Dataset]

  /**
    * Return a list of all the datasets the user can view or has created.
    */
  def listUser(user: Option[User]): List[Dataset]

  def listUserTrash(user: Option[User], limit: Integer ) : List[Dataset]

  /**
   * Get dataset.
   */
  def get(id: UUID): Option[Dataset]

  def get(ids: List[UUID]): DBResult[Dataset]

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

  /**
   * Return a list of tags and counts found in sections
   */
  def getTags(user: Option[User]): Map[String, Long]

  def modifyRDFOfMetadataChangedDatasets()

  def dumpAllDatasetGroupings(): List[String]
  
  def dumpAllDatasetMetadata(): List[String]

  def modifyRDFUserMetadata(id: UUID, mappingNumber: String="1")

  def addMetadata(id: UUID, json: String)

  def addXMLMetadata(id: UUID, fileId: UUID, json: String)

  def addUserMetadata(id: UUID, json: String)

  /** Change the metadataCount field for a dataset */
  def incrementMetadataCount(id: UUID, count: Long)

  /**
   * Add file to dataset.
   */
  def addFile(datasetId: UUID, file: File)

  /**
   * Remove file from dataset.
   */
  def removeFile(datasetId: UUID, fileId: UUID)

  /**
   * Add Folder to dataset.
   */
  def addFolder(datasetId: UUID, folderId: UUID)

  /**
   * Remove folder from dataset.
   */
  def removeFolder(datasetId: UUID, folderId: UUID)

  /**
   * Set new thumbnail.
   */
  def createThumbnail(datasetId: UUID)

  /**
   * Update thumbnail used to represent this dataset.
   */
  def updateThumbnail(datasetId: UUID, thumbnailId: UUID)

  def selectNewThumbnailFromFiles(datasetId: UUID)

  /** Queue all datasets to be indexed in Elasticsearch. */
  def indexAll()

  /** Queue a dataset to be indexed in Elasticsearch. */
  def index(id: UUID)

  def removeTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def removeTag(id: UUID, tagId: UUID)

  def removeAllTags(id: UUID)

  def getUserMetadataJSON(id: UUID): String

  def searchUserMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset]

  def searchAllMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset]

  def removeDataset(id: UUID, host: String, apiKey: Option[String], user: Option[User])

  def findOneByFileId(file_id: UUID): Option[Dataset]

  def findByFileIdDirectlyContain(file_id: UUID): List[Dataset]

  def findByFileIdAllContain(file_id: UUID): List[Dataset]

  def findNotContainingFile(file_id: UUID): List[Dataset]

  def findByTag(tag: String, user: Option[User]): List[Dataset]

  def findByTag(tag: String, start: String, limit: Integer, reverse: Boolean, user: Option[User]): List[Dataset]

  def getMetadata(id: UUID): Map[String, Any]

  def getUserMetadata(id: UUID): scala.collection.mutable.Map[String, Any]

  def getTechnicalMetadataJSON(id: UUID): String

  def getXMLMetadataJSON(id: UUID): String

  def removeXMLMetadata(id: UUID, fileId: UUID)

  def addTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String]) : List[Tag]

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

  def updateAuthorFullName(userId: UUID, fullName: String)

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
  
  /**
   * Add a creator to the end of the dataset's list of creators
   */
  def addCreator(id: UUID, creator: String)
  
  /**
   * Remove a creator from the dataset's list of creators
   */
  def removeCreator(id: UUID, creator: String)
  
  /**
   * Move a creator to a new position in the dataset's list of creators
   */
  def moveCreator(id: UUID, creator: String, position: Integer)

  def incrementViews(id: UUID, user: Option[User]): (Int, Date)

  def incrementDownloads(id: UUID, user: Option[User])

  def getMetrics(): Iterator[Dataset]

}
