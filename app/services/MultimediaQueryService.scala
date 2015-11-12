package services


import java.io.InputStream
import com.novus.salat.dao.SalatMongoCursor
import models.{UUID, MultimediaFeatures, TempFile}
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
   * Update thumbnail used to represent this query.
   */
  def updateThumbnail(queryId: UUID, thumbnailId: UUID)  
  
  /**
   * Save a file from an input stream.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): Option[TempFile]
  
  /**
   * Get the input stream of a file given a file id.
   * Returns input stream, file name, content type, content length.
   */
  def get(id: UUID): Option[(InputStream, String, String, Long)]
  
  /**
   * List all files in the system.
   */
  def listFiles(): List[TempFile]
  
  
  /**
   * Get file metadata.
   */
  def getFile(id: UUID): Option[TempFile]
  
  /**
   * Store file metadata.
   */
  def storeFileMD(id: UUID, filename: String, contentType: Option[String]): Option[TempFile]

  def findFeatureBySection(sectionId: UUID): Option[MultimediaFeatures]

  def updateFeatures(multimediaFeature: MultimediaFeatures, sectionId: UUID, features: List[JsObject])

  def insert(multimediaFeature: MultimediaFeatures)

  def listAll(): SalatMongoCursor[MultimediaFeatures]
}