package services.mongodb

import java.io.InputStream
import javax.inject.Inject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.TypeImports._
import play.api.Logger
import play.api.Play._
import scala.Some
import org.bson.types.ObjectId
import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import services.{ByteStorageService, ThumbnailService}
import models.{UUID, Thumbnail}
import MongoContext.context

/**
 * Created by lmarini on 2/27/14.
 */
class MongoDBThumbnailService @Inject()(storage: ByteStorageService) extends ThumbnailService {

  def get(thumbnailId: UUID): Option[Thumbnail] = {
    Thumbnail.findOneById(new ObjectId(thumbnailId.stringify))
  }

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    // create the element to hold the metadata
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => {
        Logger.error("No MongoSalatPlugin")
        return ""
      }
      case Some(x) =>  x.gridFS("thumbnails")
    }

    // required to avoid race condition on save
    files.db.setWriteConcern(WriteConcern.Safe)

    // use a special case if the storage is in mongo as well
    val usemongo = current.configuration.getBoolean("medici2.mongodb.storeThumbnails").getOrElse(storage.isInstanceOf[MongoDBByteStorage])
    val mongoFile = if (usemongo) {

      // leverage of the fact that we save in mongo
      val x = files.createFile(inputStream)
      val id = UUID(x.getAs[ObjectId]("_id").get.toString)
      x.put("path", id)
      x
    } else {
      // write empty array
      val x = files.createFile(Array[Byte]())
      val id = UUID(x.getAs[ObjectId]("_id").get.toString)

      // save the bytes, metadata goes to mongo
      x.put("path", storage.save(inputStream, "thumbnails", id))
      x
    }

    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.filename = filename
    mongoFile.contentType = ct
    mongoFile.save
    mongoFile.getAs[ObjectId]("_id").get.toString
  }

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "thumbnails")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
      case Some(file) => {
        // use a special case if the storage is in mongo as well
        val usemongo = current.configuration.getBoolean("medici2.mongodb.storeThumbnails").getOrElse(storage.isInstanceOf[MongoDBByteStorage])
        val inputStream = if (usemongo) {
          file.inputStream
        } else {
          file.getAs[String]("path") match {
            case Some(path) => {
              storage.load(path, "thumbnails") match {
                case Some(is) => is
                case None => return None
              }
            }
            case None => return None
          }
        }
        Some((inputStream,
          file.getAs[String]("filename").getOrElse("unknown-name"),
          file.getAs[String]("contentType").getOrElse("unknown"),
          file.getAs[Long]("length").getOrElse(0l)))
      }
      case None => None
    }
  }

  def remove(id: UUID): Unit = {
    // finally delete the actual file
    val usemongo = current.configuration.getBoolean("medici2.mongodb.storeThumbnails").getOrElse(storage.isInstanceOf[MongoDBByteStorage])
    if (usemongo) {
      val files = GridFS(SocialUserDAO.dao.collection.db, "thumbnails")
      files.remove(new ObjectId(id.stringify))
    } else {
      storage.delete(id.stringify, "thumbnails")
    }
  }
}

object Thumbnail extends ModelCompanion[Thumbnail, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Thumbnail, ObjectId](collection = x.collection("thumbnails.files")) {}
  }
}
