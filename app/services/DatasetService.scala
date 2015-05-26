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
   * List datasets that belong to a specific space. Empty list is returned if there
   * are none that apply.
   *
   * @param spaceId The identifier for the space to be checked
   *
   * @return A List of Dataset objects that are assigned to the specified Space.
   *
   */
  def listDatasetsBySpace(spaceId: UUID): List[Dataset]

  /**
   * List of Datasets for a given Space, but only the requested amount. An empty
   * list is returned if none found.
   *
   * @param spaceId Identifies the space requested
   * @param limit Limit the size of the list to this number of datasets
   * @return List of datasets attached to the given space, bounded by 'limit'.
   *
   */
  def listDatasetsBySpaceWithLimit(spaceId: UUID, limit: Int): List[Dataset]

  /**
   * Get dataset.
   */
  def get(id: UUID): Option[Dataset]

  /**
   * Insert dataset.
   */
  def insert(dataset: Dataset): Option[String]

  /**
   * Lastest dataset in chronological order.
   */
  def latest(): Option[Dataset]

  /**
   * First dataset in chronological order.
   */
  def first(): Option[Dataset]

  /**
   *
   */
  def listInsideCollection(collectionId: UUID) : List[Dataset]

  /**
   * Check if a dataset is in a specific collection.
   */
  def isInCollection(dataset: Dataset, collection: Collection): Boolean

  /**
   * Get the id of a file based on its filename and dataset it belongs to.
   */
  def getFileId(datasetId: UUID, filename: String): Option[UUID]

  /**
   * Get JSON representation.
   */
  def toJSON(dataset: Dataset): JsValue

  /**
   * Check if dataset belongs to a collection.
   */
  def isInCollection(datasetId: UUID, collectionId: UUID): Boolean


  def modifyRDFOfMetadataChangedDatasets()



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

}

