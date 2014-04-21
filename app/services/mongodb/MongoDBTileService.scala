package services.mongodb

import services.{PreviewService, TileService}
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import MongoContext.context
import play.api.Play.current
import java.io.InputStream
import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.commons.MongoDBObject
import models.{UUID, Tile}
import play.api.libs.json.{JsValue, JsObject}
import com.mongodb.casbah.Imports._
import scala.Some
import com.mongodb.WriteConcern
import play.api.libs.json.Json._
import scala.Some
import play.api.Logger
import javax.inject.{Inject, Singleton}

/**
 * Created by lmarini on 2/27/14.
 */
@Singleton
class MongoDBTileService @Inject() (previews: PreviewService) extends TileService {

  def get(tileId: UUID): Option[Tile] = {
    TileDAO.findOneById(new ObjectId(tileId.stringify))
  }

  def updateMetadata(tileId: UUID, previewId: UUID, level: String, json: JsValue) {
    json match {
    case JsObject(fields) => {
      previews.get(previewId) match {
        case Some(preview) => {
          get(tileId) match {
            case Some(tile) =>
              val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
              TileDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(tileId.stringify)),
                $set("metadata" -> metadata, "preview_id" -> new ObjectId(previewId.stringify), "level" -> level), false, false, WriteConcern.SAFE)
            case None => Logger.error("Tile not found")
          }
        }
        case None => Logger.error("Preview not found " + previewId)
      }
    }
    }
  }

  def findTile(previewId: UUID, filename: String, level: String): Option[Tile] = {
    try {
      val theTile = TileDAO.find(MongoDBObject("preview_id" -> new ObjectId(previewId.stringify), "filename" -> filename, "level" -> level)).toList.head
      return Option(theTile)
    } catch {
      case e: NoSuchElementException => return None
    }
  }

  def findByPreviewId(previewId: UUID): List[Tile] = {
    TileDAO.find(MongoDBObject("preview_id" -> new ObjectId(previewId.stringify))).toList
  }

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => x.gridFS("tiles")
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
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "tiles")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
      case Some(file) => Some(file.inputStream,
        file.getAs[String]("filename").getOrElse("unknown-name"),
        file.getAs[String]("contentType").getOrElse("unknown"),
        file.getAs[Long]("length").getOrElse(0))
      case None => None
    }
  }
}

object TileDAO extends ModelCompanion[Tile, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Tile, ObjectId](collection = x.collection("tiles.files")) {}
  }
}
