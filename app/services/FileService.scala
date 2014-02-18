package services


import java.io.InputStream
import models.{Dataset, File, Comment}
import securesocial.core.Identity
import com.mongodb.casbah.Imports._
import play.api.libs.json.{JsObject, JsArray, JsValue}

/**
 * Generic file service to store blobs of files and metadata about them.
 *
 * @author Luigi Marini
 *
 */
abstract class FileService {
  /**
   * Save a file from an input stream.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String], author: Identity, showPreviews: String = "DatasetLevel"): Option[File]
  
  /**
   * Get the input stream of a file given a file id.
   * Returns input stream, file name, content type, content length.
   */
  def getBytes(id: String): Option[(InputStream, String, String, Long)]
  
  /**
   * List all files in the system.
   */
  def listFiles(): List[File]
  
  /**
   * List files after a specified date.
   */
  def listFilesAfter(date: String, limit: Int): List[File]
  
  /**
   * List files before a specified date.
   */
  def listFilesBefore(date: String, limit: Int): List[File]
  
  /**
   * Get file metadata.
   */
  def get(id: String): Option[File]
  
  /**
   * Store file metadata.
   */
  def storeFileMD(id: String, filename: String, contentType: Option[String], author: Identity): Option[File]

  def index(id: String)

  // TODO return JsValue
  def getXMLMetadataJSON(id: String): String
  
  def modifyRDFOfMetadataChangedFiles()
  
  def modifyRDFUserMetadata(id: String, mappingNumber: String="1")

  def isInDataset(file: File, dataset: Dataset): Boolean

  def removeTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def addMetadata(fileId: String, metadata: JsValue)

  def listOutsideDataset(dataset_id: String): List[File]

  def getMetadata(id: String): scala.collection.immutable.Map[String,Any]

  def getUserMetadata(id: String): scala.collection.mutable.Map[String,Any]

  def getUserMetadataJSON(id: String): String

  def getTechnicalMetadataJSON(id: String): String

  def addVersusMetadata(id: String, json: JsValue)

  def getJsonArray(list: List[JsObject]): JsArray

  def addUserMetadata(id: String, json: String)

  def addXMLMetadata(id: String, json: String)

  def findByTag(tag: String): List[File]

  def findIntermediates(): List[File]

  def addTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String])

  def removeAllTags(id: String)

  def comment(id: String, comment: Comment)

  def setIntermediate(id: String)

  def renameFile(id: String, newName: String)
  def setContentType(id: String, newType: String)

  def setUserMetadataWasModified(id: String, wasModified: Boolean)

  def removeFile(id: String)

  def removeTemporaries()

  def findMetadataChangedFiles(): List[File]

  def searchUserMetadataFormulateQuery(requestedMetadataQuery: Any): List[File]

  def searchMetadataFormulateQuery(requestedMap: java.util.LinkedHashMap[String,Any], root: String): MongoDBObject

  def removeOldIntermediates()
}
