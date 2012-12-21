package services


import java.io.InputStream
import models.File
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.DBObject
import com.mongodb.casbah.gridfs.JodaGridFSDBFile

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
  def save(inputStream: InputStream, filename: String): String
  
  /**
   * Get the input stream of a file given a file id.
   */
  def get(id: String): Option[(InputStream, String)]
  
  /**
   * List all files in the system.
   */
  def listFiles(): List[File]
  
  /**
   * Get file metadata.
   */
  def getFile(id: String): Option[File]
  
  /**
   * Store file metadata.
   */
  def storeFileMD(id: String, filename: String): String
}
