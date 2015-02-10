package services.mongodb

import com.mongodb.casbah.WriteConcern
import services.{ByteStorageService, PreviewService, TileService}
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
import play.api.Logger
import javax.inject.{Inject, Singleton}

/**
 * Created by lmarini on 2/27/14.
 */
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
    // create the element to hold the metadata
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => {
        Logger.error("No MongoSalatPlugin")
        return ""
      }
      case Some(x) =>  x.gridFS("tiles")
    }

    // required to avoid race condition on save
    files.db.setWriteConcern(WriteConcern.Safe)

    // use a special case if the storage is in mongo as well
    val usemongo = current.configuration.getBoolean("medici2.mongodb.storeTiles").getOrElse(storage.isInstanceOf[MongoDBByteStorage])
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
      x.put("path", storage.save(inputStream, "tiles", id))
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
    val files = GridFS(TileDAO.dao.collection.db, "tiles")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
      case Some(file) => {
        // use a special case if the storage is in mongo as well
        val usemongo = current.configuration.getBoolean("medici2.mongodb.storeTiles").getOrElse(storage.isInstanceOf[MongoDBByteStorage])
        val inputStream = if (usemongo) {
          file.inputStream
        } else {
          file.getAs[String]("path") match {
            case Some(path) => {
              storage.load(path, "tiles") match {
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
    val usemongo = current.configuration.getBoolean("medici2.mongodb.storeTiles").getOrElse(storage.isInstanceOf[MongoDBByteStorage])
    if (usemongo) {
      val files = GridFS(TileDAO.dao.collection.db, "tiles")
      files.remove(new ObjectId(id.stringify))
    } else {
      storage.delete(id.stringify, "tiles")
    }
  }
}

object TileDAO extends ModelCompanion[Tile, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Tile, ObjectId](collection = x.collection("tiles.files")) {}
  }
}
