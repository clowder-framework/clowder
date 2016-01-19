package services.mongodb

import java.io.InputStream
import org.bson.types.ObjectId
import services.{ByteStorageService, ThumbnailService}
import models.{UUID, Thumbnail}
import com.mongodb.casbah.commons.MongoDBObject
import javax.inject.{Inject}
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current




/**
 * Created by lmarini on 2/27/14.
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
    (for (thumbnail <- ThumbnailDAO.find(MongoDBObject())) yield thumbnail).toList
  }

  def get(thumbnailId: UUID): Option[Thumbnail] = {
    ThumbnailDAO.findOneById(new ObjectId(thumbnailId.stringify))
  }

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    MongoUtils.writeBlob[Thumbnail](inputStream, filename, contentType, Map.empty[String, AnyRef], "thumbnails", "medici2.mongodb.storeThumbnails").fold("")(_._1.stringify)
  }

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    MongoUtils.readBlob(id, "thumbnails", "medici2.mongodb.storeThumbnails")
  }

  /**
   * Remove blob.
   */
  def remove(id: UUID): Unit = {
    MongoUtils.removeBlob(id, "thumbnails", "medici2.mongodb.storeThumbnails")
  }

  object ThumbnailDAO extends ModelCompanion[Thumbnail, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[Thumbnail, ObjectId](collection = x.collection("thumbnails.files")) {}
    }
  }

}
