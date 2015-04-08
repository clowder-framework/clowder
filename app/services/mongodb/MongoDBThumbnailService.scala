package services.mongodb

import java.io.InputStream
import javax.inject.Inject
import play.api.Play._
import org.bson.types.ObjectId
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
    MongoUtils.writeBlob(inputStream, filename, contentType, Map.empty[String, AnyRef], "thumbnails", "medici2.mongodb.storeThumbnails").fold("")(_.stringify)
  }

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    MongoUtils.readBlob(id, "thumbnails", "medici2.mongodb.storeThumbnails")
  }

  def remove(id: UUID): Unit = {
    MongoUtils.removeBlob(id, "thumbnails", "medici2.mongodb.storeThumbnails")
  }
}

object Thumbnail extends ModelCompanion[Thumbnail, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Thumbnail, ObjectId](collection = x.collection("thumbnails.files")) {}
  }
}
