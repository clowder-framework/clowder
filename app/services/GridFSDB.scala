package services

import java.io.InputStream
import play.Logger
import org.bson.types.ObjectId
import com.mongodb.casbah.gridfs.JodaGridFS
import models.SocialUserDAO
import models.FileDAO
import com.mongodb.casbah.commons.MongoDBObject

/**
 * Use GridFS to store blobs.
 * 
 * @author Luigi Marini
 *
 */
trait GridFSDB {

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String): String = {
    val files = JodaGridFS(FileDAO.dao.collection.db, "uploads")
    val mongoFile = files.createFile(inputStream)
    Logger.info("Uploading file " + filename)
    mongoFile.filename = filename
    mongoFile.contentType = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    mongoFile.save
    mongoFile.getAs[ObjectId]("_id").get.toString
  }

  /**
   * Get blob.
   */
  def get(id: String): Option[(InputStream, String)] = {
    val files = JodaGridFS(SocialUserDAO.dao.collection.db, "uploads")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      case Some(file) => Some(file.inputStream, file.getAs[String]("filename").getOrElse("unknown-name"))
      case None => None
    }
  }
  
}
