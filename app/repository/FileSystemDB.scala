package repository

import java.io.InputStream
import play.api.Play
import java.util.UUID
import play.Logger
import java.io.FileInputStream
import java.io.FileOutputStream
import com.mongodb.casbah.gridfs.JodaGridFS
import models.FileDAO
import org.bson.types.ObjectId
import java.io.File
import com.mongodb.casbah.commons.MongoDBObject

/**
 * Store blobs on the file system.
 *
 * @author Luigi Marini
 *
 */
trait FileSystemDB {
  this: MongoFileDB =>

  /**
   * Save a file to the file system and store metadata about it in Mongo.
   */
  def save(inputStream: InputStream, filename: String): String = {
    Play.current.configuration.getString("files.path") match {
      case Some(path) => {
        val id = UUID.randomUUID().toString()
        val filePath = if (path.last != '/') path + "/" + id else path + id
        Logger.info("Copying file to " + filePath)
        // FIXME is there a better way than casting to FileInputStream?
        val f = inputStream.asInstanceOf[FileInputStream].getChannel()
        val f2 = new FileOutputStream(new File(filePath)).getChannel()
        f.transferTo(0, f.size(), f2)
        f2.close()
        f.close()

        // store metadata to mongo
        storeFileMD(id, filename)
      }
      case None => {
        Logger.error("Could not store file on disk")
        "ERROR"
      }
    }
  }

  /**
   * Get the bytes of a file from Mongo and the file name.
   */
  def get(id: String): Option[(InputStream, String)] = {
    Play.current.configuration.getString("files.path") match {
      case Some(path) => {
        val files = JodaGridFS(FileDAO.dao.collection.db, "uploads")
        files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
          case Some(file) => {
            file.getAs[String]("path") match {
              case Some(relativePath) => {
                val filePath = if (path.last != '/') path + "/" + relativePath else path + relativePath
                Logger.info("Serving file " + filePath)
                Some(new FileInputStream(filePath), file.getAs[String]("filename").getOrElse("unknown-name"))
              }
              case None => None
            }
          }
          case None => None
        }
      }
      case None => None
    }
  }
}