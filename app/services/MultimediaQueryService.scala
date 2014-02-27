package services


import java.io.InputStream
import models.{MultimediaFeatures, TempFile}
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.DBObject
import com.mongodb.casbah.gridfs.JodaGridFSDBFile
import play.api.libs.json.JsObject

/**
 * Generic file service to store blobs of files and metadata about them.
 *
 * @author Luigi Marini
 *
 */
abstract class MultimediaQueryService {
  
  /**
   * Save a file from an input stream.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): Option[TempFile]
  
  /**
   * Get the input stream of a file given a file id.
   * Returns input stream, file name, content type, content length.
   */
  def get(id: String): Option[(InputStream, String, String, Long)]
  
  /**
   * List all files in the system.
   */
  def listFiles(): List[TempFile]
  
  
  /**
   * Get file metadata.
   */
  def getFile(id: String): Option[TempFile]
  
  /**
   * Store file metadata.
   */
  def storeFileMD(id: String, filename: String, contentType: Option[String]): Option[TempFile]

  def findFeatureBySection(sectionId: String): Option[MultimediaFeatures]

  def updateFeatures(multimediaFeature: MultimediaFeatures, sectionId: String, features: List[JsObject])

  def insert(multimediaFeature: MultimediaFeatures)

  def listAll(): List[MultimediaFeatures]
}
