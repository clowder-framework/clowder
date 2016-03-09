package services.mongodb

import com.mongodb.casbah.WriteConcern
import services.{ByteStorageService, PreviewService, TileService}
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import MongoContext.context
import play.api.Play.current
import java.io.InputStream
import com.mongodb.casbah.commons.MongoDBObject
import models.{UUID, Tile}
import play.api.libs.json.{JsValue, JsObject}
import com.mongodb.casbah.Imports._
import play.api.Logger
import javax.inject.{Inject, Singleton}

import util.FileUtils

@Singleton
class MongoDBTileService @Inject() (previews: PreviewService, storage: ByteStorageService) extends TileService {

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
                $set("metadata" -> metadata, "preview_id" -> new ObjectId(previewId.stringify), "level" -> level), false, false, WriteConcern.Safe)
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
      Option(theTile)
    } catch {
      case e: NoSuchElementException => None
    }
  }

  def findByPreviewId(previewId: UUID): List[Tile] = {
    TileDAO.find(MongoDBObject("preview_id" -> new ObjectId(previewId.stringify))).toList
  }

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    ByteStorageService.save(inputStream, TileDAO.COLLECTION) match {
      case Some(x) => {
        val tile = Tile(UUID.generate(), x._1, x._2, None, Some(filename), FileUtils.getContentType(filename, contentType), None, x._4)
        TileDAO.save(tile)
        tile.id.stringify
      }
      case None => ""
    }
  }

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    get(id).flatMap { x =>
      ByteStorageService.load(x.loader, x.loader_id, TileDAO.COLLECTION).map((_, x.filename.getOrElse(""), x.contentType, x.length))
    }
  }

  def remove(id: UUID): Unit = {
    get(id).foreach { x =>
      ByteStorageService.delete(x.loader, x.loader_id, TileDAO.COLLECTION)
      TileDAO.remove(x)
    }
  }
}

object TileDAO extends ModelCompanion[Tile, ObjectId] {
  val COLLECTION = "tiles"

  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Tile, ObjectId](collection = x.collection(COLLECTION)) {}
  }
}
