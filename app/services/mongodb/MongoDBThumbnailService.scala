package services.mongodb

import java.io.InputStream
import javax.inject.Inject
import play.api.Play._
import org.bson.types.ObjectId
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import services.{ByteStorageService, ThumbnailService}
import models.{UUID, Thumbnail}
import MongoContext.context
import com.mongodb.casbah.commons.MongoDBObject




import services._
import models._
import com.mongodb.casbah.commons.MongoDBObject
import java.text.SimpleDateFormat
import _root_.util.{Parsers, License}
import com.novus.salat._

import scala.collection.mutable.ListBuffer
import Transformation.LidoToCidocConvertion
import java.util.{Calendar, ArrayList}
import java.io._
import org.apache.commons.io.FileUtils
import org.json.JSONObject
import play.api.libs.json.{Json, JsValue}
import com.mongodb.util.JSON
import java.nio.file.{FileSystems, Files}
import java.nio.file.attribute.BasicFileAttributes
import collection.JavaConverters._
import scala.collection.JavaConversions._
import javax.inject.{Inject, Singleton}
import com.mongodb.casbah.WriteConcern
import play.api.Logger
import scala.util.parsing.json.JSONArray
import play.api.libs.json.JsArray
import models.File
import play.api.libs.json.JsObject
import java.util.Date
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.Imports._
import securesocial.core.Identity



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
    MongoUtils.writeBlob[Thumbnail](inputStream, filename, contentType, Map.empty[String, AnyRef], "thumbnails", "medici2.mongodb.storeThumbnails").fold("")(_.stringify)
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


  def removeThumbnail(id: UUID) {
    get(id) match {
      case Some(thumbnail) => {
        MongoUtils.removeBlob(id, "thumbnails", "medici2.mongodb.storeThumbnails")
      }
      case None => {
        Logger.debug("Thumbnail file not found")
      }
    }
  }

  object ThumbnailDAO extends ModelCompanion[Thumbnail, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[Thumbnail, ObjectId](collection = x.collection("thumbnails.files")) {}
    }
  }

}
