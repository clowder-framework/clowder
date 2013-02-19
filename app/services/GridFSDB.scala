package services

import java.io.InputStream
import play.Logger
import play.api.Play.current
import org.bson.types.ObjectId
import models.SocialUserDAO
import models.FileDAO
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.gridfs.GridFS

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
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) =>  x.gridFS("uploads")
    }
    val mongoFile = files.createFile(inputStream)
    Logger.info("Uploading file " + filename)
    mongoFile.filename = filename
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.contentType = ct
    mongoFile.save
    mongoFile.getAs[ObjectId]("_id").get.toString
  }

  /**
   * Get blob.
   */
  def get(id: String): Option[(InputStream, String)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "uploads")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      case Some(file) => Some(file.inputStream, file.getAs[String]("filename").getOrElse("unknown-name"))
      case None => None
    }
  }
  
}
