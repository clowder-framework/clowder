package services.mongodb

import java.io.InputStream

import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.gridfs.GridFSInputFile
import models.UUID
import org.bson.types.ObjectId
import play.api.Logger
import play.api.Play._
import services.ByteStorageService

/**
 * Store the bytes in a mongo gridFS.
 *
 * @author Rob Kooper
 */
class MongoDBByteStorage extends ByteStorageService {
  /**
   * Save the bytes to mongo, the prefix is used for the collection and id is
   * ignored.
   */
  def save(inputStream: InputStream, collection: String, id: UUID): Option[String] = {
    current.plugin[MongoSalatPlugin] match {
      case None => {
        Logger.error("No MongoSalatPlugin")
        None
      }
      case Some(x) => {
        val files = x.gridFS(collection)

        // required to avoid race condition on save
        files.db.setWriteConcern(WriteConcern.Safe)

        // save the bytes
        Logger.debug("Saving file to " + collection)
        val file = files.createFile(inputStream)
        file.getAs[String]("_id")
      }
    }
  }

  /**
   * Get the bytes from Mongo
   */
  def load(id: String, collection: String): Option[InputStream] = {
    current.plugin[MongoSalatPlugin] match {
      case None => {
        Logger.error("No MongoSalatPlugin")
        None
      }
      case Some(x) => {
        val files = x.gridFS(collection)
        files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
          case Some(file) => Some(file.inputStream)
          case None => None
        }
      }
    }
  }

  /**
   * Delete actualy bytes from Mongo
   */
  def delete(id: String, collection: String): Boolean = {
    current.plugin[MongoSalatPlugin] match {
      case None => {
        Logger.error("No MongoSalatPlugin")
        false
      }
      case Some(x) => {
        val files = x.gridFS(collection)
        files.remove(new ObjectId(id))
        true
      }
    }
  }
}
