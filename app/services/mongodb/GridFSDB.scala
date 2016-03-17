package services.mongodb

import java.io.InputStream
import play.Logger
import play.api.Play.current
import org.bson.types.ObjectId
import models.{User, UUID, File}
import com.mongodb.casbah.WriteConcern

/**
 * Use GridFS to store blobs.
 *
 *
 */
trait GridFSDB {

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String], author: User, showPreviews: String = "DatasetLevel"): Option[File] = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) =>  x.gridFS("uploads")
    }

    // required to avoid race condition on save
    files.db.setWriteConcern(WriteConcern.Safe)

    val mongoFile = files.createFile(inputStream)
    Logger.debug("Uploading file " + filename)
    mongoFile.filename = filename
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.contentType = ct
    mongoFile.put("showPreviews", showPreviews)
    mongoFile.put("author", SocialUserDAO.dao.toDBObject(author))
    mongoFile.save
    val oid = mongoFile.getAs[ObjectId]("_id").get

    Some(File(UUID(oid.toString), None, mongoFile.filename.get, author, mongoFile.uploadDate, mongoFile.contentType.get, mongoFile.length, showPreviews))
  }

  /**
   * Get blob.
   */
  def getBytes(id: UUID): Option[(InputStream, String, String, Long)] = {
    MongoUtils.readBlob(id, "uploads", "nevereverset")
  }
}
