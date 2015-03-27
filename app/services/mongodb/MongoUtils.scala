package services.mongodb

import java.io.InputStream

import com.mongodb.gridfs.GridFS
import com.mongodb.WriteConcern
import models.UUID
import org.bson.types.ObjectId
import play.Logger
import play.api.Play._
import services.{ByteStorageService, DI}

object MongoUtils {
  val storage: ByteStorageService = DI.injector.getInstance(classOf[ByteStorageService])

  def writeBlob(inputStream: InputStream, filename: String, contentType: Option[String], extra: Map[String, AnyRef], collection: String, useMongoProperty: String): Option[UUID] = {
    current.plugin[MongoSalatPlugin] match {
      case None => {
        Logger.error("No MongoSalatPlugin")
        None
      }
      case Some(x) => {
        val files = new GridFS(x.getDB.underlying, collection)
        files.getDB.setWriteConcern(WriteConcern.SAFE)

        // use a special case if the storage is in mongo as well
        val usemongo = current.configuration.getBoolean(useMongoProperty).getOrElse(storage.isInstanceOf[MongoDBByteStorage])
        val (mongoFile, id) = if (usemongo) {
          // leverage of the fact that we save in mongo
          val x = files.createFile(inputStream)
          val id = UUID(x.getId.toString)
          x.put("path", id)
          (x, id)
        } else {
          // write empty array
          val x = files.createFile(Array[Byte]())
          val id = UUID(x.getId.toString)
          x.put("path", storage.save(inputStream, collection, id))
          (x, id)
        }

        var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
        if (ct == play.api.http.ContentTypes.BINARY) {
          ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
        }

        mongoFile.setFilename(filename)
        mongoFile.setContentType(ct)
        extra.foreach{e => mongoFile.put(e._1, e._2)}
        mongoFile.save()

        Some(id)
      }
    }
  }

  def readBlob(id: UUID, collection: String, useMongoProperty: String): Option[(InputStream, String, String, Long)] = {
    current.plugin[MongoSalatPlugin] match {
      case None => {
        Logger.error("No MongoSalatPlugin")
        None
      }
      case Some(x) => {
        val files = new GridFS(x.getDB.underlying, collection)
        val file = files.findOne(new ObjectId(id.stringify))
        if (file == null) {
          None
        } else {
          // use a special case if the storage is in mongo as well
          val usemongo = current.configuration.getBoolean(useMongoProperty).getOrElse(storage.isInstanceOf[MongoDBByteStorage])
          val inputStream = if (usemongo) {
            file.getInputStream
          } else {
            val path = file.get("path").asInstanceOf[String]
            if (path == null) {
              return None
            } else {
              storage.load(path, collection) match {
                case Some(is) => is
                case None => return None
              }
            }
          }
          val filename = if (file.getFilename == null) "unknown-name" else file.getFilename
          val contentType = if (file.getContentType == null) "unknown" else file.getContentType
          Some((inputStream, filename, contentType, file.getLength))
        }
      }
    }
  }

  def removeBlob(id: UUID, collection: String, useMongoProperty: String): Boolean = {
    current.plugin[MongoSalatPlugin] match {
      case None => {
        Logger.error("No MongoSalatPlugin")
        false
      }
      case Some(x) => {
        val usemongo = current.configuration.getBoolean(useMongoProperty).getOrElse(storage.isInstanceOf[MongoDBByteStorage])
        val files = new GridFS(x.getDB.underlying, collection)
        if (!usemongo) {
          val file = files.find(new ObjectId(id.stringify))
          if (file != null) {
            val path = file.get("path").asInstanceOf[String]
            if (path != null) {
              storage.delete(path, collection)
            }
          }
        }
        files.remove(new ObjectId(id.stringify))
        true
      }
    }
  }
}
