package repository

import com.mongodb.casbah.Imports._
import java.io.File
import play.api.Play.current
import se.radley.plugin.salat._
import play.api._
import java.io.InputStream
import com.mongodb.casbah.gridfs.JodaGridFS
import models.MongoContext
import models.SocialUserDAO

/**
 * Save files in MongoDB.
 * 
 * @author Luigi Marini
 *
 */
trait MongoDBFileRepositoryComponent {

  val fileRepository: FileRepository
  
  class FileRepository {
    
    def save(inputStream: InputStream, filename: String): String = {
//      val files = gridFS("uploads") 
      val files = JodaGridFS(SocialUserDAO.dao.collection.db, "uploads")
      val mongoFile = files.createFile(inputStream)
      Logger.info("Uploading file " + filename)
      mongoFile.filename = filename
      mongoFile.contentType = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
      mongoFile.save
      mongoFile.getAs[ObjectId]("_id").get.toString
    }
    
    def get(id: String): Option[(InputStream, String)] = {
//      val files = gridFS("uploads")
      val files = JodaGridFS(SocialUserDAO.dao.collection.db, "uploads")
      files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
        case Some(file) => Some(file.inputStream, file.getAs[String]("filename").getOrElse("unknown-name"))
        case None => None
      }
    }
  }
}