package services

import java.io.InputStream
import models.File
import com.mongodb.casbah.MongoConnection
import com.mongodb.casbah.gridfs.JodaGridFS
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import com.mongodb.DBObject
import models.SocialUserDAO
import play.Logger
import com.mongodb.casbah.gridfs.JodaGridFSDBFile
import models.FileDAO

/**
 * Access file metedata from MongoDB.
 *
 * @author Luigi Marini
 *
 */
trait MongoFileDB {

  /**
   * List all files.
   */
  def listFiles(): List[File] = {
    (for (file <- FileDAO.find(MongoDBObject())) yield file).toList
  }

  /**
   * Get file metadata.
   */
  def getFile(id: String): Option[File] = {
    FileDAO.findOne(MongoDBObject("_id" -> new ObjectId(id)))
  }

  /**
   * Store file metadata.
   */
  def storeFileMD(id: String, filename: String): String = {
    val files = JodaGridFS(FileDAO.dao.collection.db, "uploads")
    val mongoFile = files.createFile(Array[Byte]())
    mongoFile.filename = filename
    mongoFile.contentType = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    mongoFile.put("path", id)
    mongoFile.save
    mongoFile.getAs[ObjectId]("_id").get.toString
  }
}