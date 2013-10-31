package services


import java.io.InputStream
import models.File
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.DBObject
import com.mongodb.casbah.gridfs.JodaGridFSDBFile
import securesocial.core.Identity

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
  def get(id: String): Option[(InputStream, String, String, Long)]
  
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
  def getFile(id: String): Option[File]
  
  /**
   * Store file metadata.
   */
  def storeFileMD(id: String, filename: String, contentType: Option[String], author: Identity): Option[File]
}
