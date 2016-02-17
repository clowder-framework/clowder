package services.mongodb

import java.io.InputStream
import java.security.{DigestInputStream, MessageDigest}

import com.mongodb.gridfs.GridFS
import com.mongodb.{DBObject, WriteConcern}
import models.UUID
import org.apache.commons.codec.binary.Hex
import org.apache.commons.io.input.CountingInputStream
import org.bson.types.ObjectId
import play.Logger
import play.api.Play._
import services.{ByteStorageService, DI}

/**
 * Object used to read/write/delete from mongo. This object is aware of
 * ByteStorageService. It will use mongo for the metadta of the file,
 * and the ByteStorageService for the bytes. If either the ByteStorageService
 * is set to MongoDBByteStorage or the useMongoProperty is set to true it
 * will use mongo for the bytes as well (without going through
 * ByteStorageService).
 *
 * This code is aware of an issue with Casbah, Joda (see JIRA:
 * https://opensource.ncsa.illinois.edu/jira/browse/MMDB-1573).
 */
object MongoUtils {
  val storage: ByteStorageService = DI.injector.getInstance(classOf[ByteStorageService])

  /**
   * Write the inputstream to mongo and ByteStorageService. Any extra values
   * to be written can be stored in extra. The values need to be able to be
   * converted to DBObject. Returns the (ID, sha512, length).
   */
  def writeBlob[T](inputStream: InputStream, filename: String, contentType: Option[String], extra: Map[String, AnyRef], collection: String, useMongoProperty: String)(implicit ev: Manifest[T]): Option[(UUID, String, Long)] = {
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
          val md = MessageDigest.getInstance("SHA-512")
          val dis = new DigestInputStream(inputStream, md)
          val x = files.createFile(dis)
          val id = UUID(x.getId.toString)
          x.save() // need to save to actually read the inputstream
          x.put("sha512", Hex.encodeHexString(md.digest()))
          x.put("loader", classOf[MongoDBByteStorage].getName)
          x.save() // save to add new options
          (x, id)
        } else {
          // write empty array
          val x = files.createFile(Array[Byte]())
          x.save()
          val id = UUID(x.getId.toString)
          x.put("loader", storage.getClass.getName)
          storage.save(inputStream, collection, id) match {
            case Some((path:String, sha512:String, length:Long)) => {
              x.put("path", path)
              x.put("sha512", sha512)
              x.put("length", length)
            }
            case None => {
              x.put("path", "")
              x.put("sha512", "")
              x.put("length", -1)
            }
          }
          (x, id)
        }

        var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
        if (ct == play.api.http.ContentTypes.BINARY) {
          ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
        }

        mongoFile.setFilename(filename)
        mongoFile.setContentType(ct)
        extra.foreach{e => mongoFile.put(e._1, e._2)}
        Logger.debug("_typeHint = " + ev.runtimeClass.getCanonicalName)
        mongoFile.put("_typeHint", ev.runtimeClass.getCanonicalName)
        mongoFile.save()

        Some((id, mongoFile.get("sha512").toString, mongoFile.getLength))
      }
    }
  }

  /**
   * Read from mongo and ByteStorageService. This will return a tuple that
   * contains metadata of the file as well as inputstream to the bytes.
   */
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
          val loader = file.get("loader").toString
          val inputStream = if (loader == classOf[MongoDBByteStorage].getName) {
            file.getInputStream
          } else {
            val path = file.get("path").asInstanceOf[String]
            val bss = Class.forName(loader).newInstance.asInstanceOf[ByteStorageService]
            bss.load(path, collection) match {
              case Some(is) => is
              case None => return None
            }
          }
          val filename = if (file.getFilename == null) "unknown-name" else file.getFilename
          val contentType = if (file.getContentType == null) "unknown" else file.getContentType
          Some((inputStream, filename, contentType, file.getLength))
        }
      }
    }
  }

  /**
   * Remove from mongo and ByteStorageService. This will return the metadata
   * from mongo and the bytes from ByteStorageService.
   */
  def removeBlob(id: UUID, collection: String, useMongoProperty: String): Boolean = {
    current.plugin[MongoSalatPlugin] match {
      case None => {
        Logger.error("No MongoSalatPlugin")
        false
      }
      case Some(x) => {
        val files = new GridFS(x.getDB.underlying, collection)
        val file = files.find(new ObjectId(id.stringify))
        val loader = file.get("loader")
        if (loader != null && loader.toString != classOf[MongoDBByteStorage].getName) {
          val path = file.get("path").asInstanceOf[String]
          val bss = Class.forName(loader.toString).newInstance.asInstanceOf[ByteStorageService]
          bss.delete(path, collection)
        }
        files.remove(new ObjectId(id.stringify))
        true
      }
    }
  }

  def mongoQuery(dbObject: DBObject): String = {
    dbObject.toString.replaceAll("\\{\\s*\"\\$oid\"\\s*:\\s*(\"[^\"]+\")\\}", "ObjectId($1)").replaceAll("\\{\\s*\"\\$date\"\\s*:\\s*(\"[^\"]+\")\\}", "new Date($1)")
  }
}
