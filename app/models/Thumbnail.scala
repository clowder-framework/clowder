package models

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import services.mongodb.{SocialUserDAO, MongoContext, MongoSalatPlugin}
import MongoContext.context
import play.api.Play.current
import java.io.InputStream
import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.commons.MongoDBObject

/**
 * Thumbnails for datasets and files.
 */
case class Thumbnail(
id: ObjectId = new ObjectId,
filename: Option[String] = None,
contentType: String,
length: Long)

object Thumbnail extends ModelCompanion[Thumbnail, ObjectId] {

  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Thumbnail, ObjectId](collection = x.collection("thumbnails.files")) {}
  }

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => x.gridFS("thumbnails")
    }
    val mongoFile = files.createFile(inputStream)
    //    Logger.debug("Uploading file " + filename)
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
  def getBlob(id: String): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "thumbnails")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      case Some(file) => Some(file.inputStream,
        file.getAs[String]("filename").getOrElse("unknown-name"),
        file.getAs[String]("contentType").getOrElse("unknown"),
        file.getAs[Long]("length").getOrElse(0))
      case None => None
    }
  }

}