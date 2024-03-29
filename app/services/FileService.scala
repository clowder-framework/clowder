package services

import java.io.InputStream
import java.util.Date

import models._
import com.mongodb.casbah.Imports._
import models.FileStatus.FileStatus
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

object FileService {
  val ARCHIVE_PARAMETER = ("operation" -> Json.toJson("archive"))
  val UNARCHIVE_PARAMETER = ("operation" -> Json.toJson("unarchive"))
}

/**
 * Generic file service to store blobs of files and metadata about them.
 *
 *
 */
trait FileService {

  /**
   * The number of files
   */
  def count(): Long

  /**
    * The number of files
    */
  def statusCount(): Map[FileStatus, Long]

  /**
    * The number of bytes stored
    */
  def bytes(): Long

  /**
   * Save a file from an input stream.
   */
  def save(inputStream: InputStream, filename: String, contentLength: Long, contentType: Option[String], author: MiniUser, showPreviews: String = "DatasetLevel"): Option[File]

  /**
   * Save a file object
   */
  def save(file: File): Unit

  /**
   * Get the input stream of a file given a file id.
   * Returns input stream, file name, content type, content length.
   */
  def getBytes(id: UUID): Option[(InputStream, String, String, Long)]

  /**
   * Remove the file from mongo
   */
  def removeFile(id: UUID, host: String, apiKey: Option[String], user: Option[User]) : Boolean

  /**
   * List all files in the system.
   */
  def listFiles(): List[File]
  
  /**
   * List all files in the system that are not intermediate result files generated by the extractors.
   */
  def listFilesNotIntermediate(): List[File]
  
  /**
   * List files after a specified date.
   */
  def listFilesAfter(date: String, limit: Int): List[File]
  
  /**
   * List files before a specified date.
   */
  def listFilesBefore(date: String, limit: Int): List[File]
  
  /**
   * List files for a specific user after a specified date.
   */
  def listUserFilesAfter(date: String, limit: Int, email: String): List[File]
  
  /**
   * List files for a specific user before a specified date.
   */
  def listUserFilesBefore(date: String, limit: Int, email: String): List[File]

  /**
    * Submit a single archival operation to the appropriate queue/extractor
    */
  def submitArchivalOperation(file: File, id:UUID, host: String, parameters: JsObject, apiKey: Option[String], user: Option[User])

  /**
    * Submit all archival candidates to the appropriate queue/extractor
    */
  def autoArchiveCandidateFiles()

  /**
   * Get file metadata.
   */
  def get(id: UUID): Option[File]

  def get(ids: List[UUID]): DBResult[File]

  /**
    * Set the file status
    */
  def setStatus(id: UUID, status: FileStatus): Unit

  /**
   * Lastest file in chronological order.
   */
  def latest(): Option[File]

  /**
   * Lastest x files in chronological order.
   */
  def latest(i: Int): List[File]

  /**
   * First file in chronological order.
   */
  def first(): Option[File]

  def indexAll(idx: Option[String] = None)
  
  def index(id: UUID, idx: Option[String] = None)

  /**
   * Directly insert file into database, for example if the file path is local.
   */
  def insert(file: File): Option[String]

  /**
   * Return a list of tags and counts found in sections
   */
  def getTags(user: Option[User]): Map[String, Long]

  /**
   * Update thumbnail used to represent this dataset.
   */
  def updateThumbnail(fileId: UUID, thumbnailId: UUID)

  // TODO return JsValue
  def getXMLMetadataJSON(id: UUID): String
  
  def modifyRDFOfMetadataChangedFiles()
  
  def modifyRDFUserMetadata(id: UUID, mappingNumber: String="1")

  def dumpAllFileMetadata(): List[String]

  def isInDataset(file: File, dataset: Dataset): Boolean

  def removeTags(id: UUID, tags: List[String])

  def addMetadata(fileId: UUID, metadata: JsValue)

  def listOutsideDataset(dataset_id: UUID): List[File]

  def getMetadata(id: UUID): scala.collection.immutable.Map[String,Any]

  def getUserMetadata(id: UUID): scala.collection.mutable.Map[String,Any]

  def getUserMetadataJSON(id: UUID): String

  def getTechnicalMetadataJSON(id: UUID): String

  def incrementMetadataCount(id: UUID, count: Long)

  def getVersusMetadata(id:UUID): Option[JsValue]

  def addVersusMetadata(id: UUID, json: JsValue)

  def getJsonArray(list: List[JsObject]): JsArray

  def addUserMetadata(id: UUID, json: String)

  def addXMLMetadata(id: UUID, json: String)  

  def findByTag(tag: String, user: Option[User]): List[File]

  def findByTag(tag: String, start: String, limit: Integer, reverse: Boolean, user: Option[User]): List[File]

  def findIntermediates(): List[File]

  def addTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String]) : List[Tag]

  def removeAllTags(id: UUID)

  def comment(id: UUID, comment: Comment)

  def setIntermediate(id: UUID)

  def renameFile(id: UUID, newName: String)

  def setContentType(id: UUID, newType: String)

  def setUserMetadataWasModified(id: UUID, wasModified: Boolean)

  def removeTemporaries()

  def findMetadataChangedFiles(): List[File]

  def searchAllMetadataFormulateQuery(requestedMetadataQuery: Any): List[File]

  def searchUserMetadataFormulateQuery(requestedMetadataQuery: Any): List[File]

  def searchMetadataFormulateQuery(requestedMap: java.util.LinkedHashMap[String, Any], root: String): MongoDBObject

  def removeOldIntermediates(apiKey: Option[String], user: Option[User])
  
  /**
   * Update the license data that is currently associated with the file.
   * 
   * id: The id of the file
   * licenseType: A String representing the type of license
   * rightsHolder: A String that is the free-text describing the owner of the license. Only required for certain license types
   * licenseText: Text that describes what the license is
   * licenseUrl: A reference to the license information
   * allowDownload: true or false, to allow downloading of the file or dataset. Relevant only for certain license types
   */
  def updateLicense(id: UUID, licenseType: String, rightsHolder: String, licenseText: String, licenseUrl: String, allowDownload: String)

  /**
   * Add follower to a file.
   */
  def addFollower(id: UUID, userId: UUID)

  /**
   * Remove follower from a file.
   */
  def removeFollower(id: UUID, userId: UUID)

  /**
   * Update technical metadata
   */
  def updateMetadata(fileId: UUID, metadata: JsValue, extractor_id: String)

  def updateDescription(fileId : UUID, description : String)

  def updateAuthorFullName(userId: UUID, fullName: String)

  def incrementViews(id: UUID, user: Option[User]): (Int, Date)

  def incrementDownloads(id: UUID, user: Option[User], dateOnly: Boolean = false)

  def getIterator(space: Option[String], since: Option[String], until: Option[String]): Iterator[File]

  def isInTrash(id: UUID): Boolean

}
