package services.mongodb

import java.io.InputStream
import java.util.Date
import org.bson.types.ObjectId
import services.{ByteStorageService, ThumbnailService}
import models.{Thumbnail, UUID}
import com.mongodb.casbah.commons.MongoDBObject
import javax.inject.{Inject}
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import util.FileUtils

/**
 * Use Mongodb to store thumbnails.
 */
class MongoDBThumbnailService @Inject()(storage: ByteStorageService) extends ThumbnailService {

  object MustBreak extends Exception {}


  /**
   * Count all files
   */
  def count(): Long = {
    ThumbnailDAO.count(MongoDBObject())
  }

  /**
   * List all thumbnail files.
   */
  def listThumbnails(): List[Thumbnail] = {
   ThumbnailDAO.find(MongoDBObject()).toList
  }

  def get(thumbnailId: UUID): Option[Thumbnail] = {
    ThumbnailDAO.findOneById(new ObjectId(thumbnailId.stringify))
  }

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    ByteStorageService.save(inputStream, ThumbnailDAO.COLLECTION) match {
      case Some(x) => {
        val thumbnail = Thumbnail(UUID.generate(), x._1, x._2, x._4, Some(filename), FileUtils.getContentType(filename, contentType), new Date())
        ThumbnailDAO.save(thumbnail)
        thumbnail.id.stringify
      }
      case None => ""
    }
  }

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    get(id).flatMap { x =>
      ByteStorageService.load(x.loader, x.loader_id, ThumbnailDAO.COLLECTION).map((_, x.filename.getOrElse(""), x.contentType, x.length))
    }
  }

  /**
   * Remove blob.
   */
  def remove(id: UUID): Unit = {
    get(id).foreach { x =>
      ByteStorageService.delete(x.loader, x.loader_id, ThumbnailDAO.COLLECTION)
      ThumbnailDAO.remove(x)
    }
  }

  object ThumbnailDAO extends ModelCompanion[Thumbnail, ObjectId] {
    val COLLECTION = "thumbnails"

    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[Thumbnail, ObjectId](collection = x.collection(COLLECTION)) {}
    }
  }

}
