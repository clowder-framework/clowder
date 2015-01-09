package services.filesystem

import java.io.InputStream
import javax.inject.Inject
import play.api.Play
import play.Logger
import java.io.FileInputStream
import java.io.FileOutputStream
import com.mongodb.casbah.gridfs.JodaGridFS
import org.bson.types.ObjectId
import java.io.File
import com.mongodb.casbah.commons.MongoDBObject
import securesocial.core.Identity
import services._
import services.mongodb.{FileDAO, MongoDBFileService}
import models.UUID

/**
 * Store blobs on the file system.
 *
 * @author Luigi Marini
 *
 */
class FileSystemDB @Inject() (
  datasets: DatasetService,
  collections: CollectionService,
  sections: SectionService,
  comments: CommentService,
  previews: PreviewService,
  threeD: ThreeDService,
  sparql: RdfSPARQLService)

extends MongoDBFileService(
  datasets,
  collections,
  sections,
  comments,
  previews,
  threeD,
  sparql) {

  /**
   * Save a file to the file system and store metadata about it in Mongo.
   */
  override  def save(inputStream: InputStream, filename: String, contentType: Option[String], author: Identity, showPreviews: String = "DatasetLevel"): Option[models.File] = {
    val id = UUID.generate
    generateFileName(id) match {
      case Some(filePath) => {
        Logger.info("Copying file to " + filePath)
        // FIXME is there a better way than casting to FileInputStream?
        val f = inputStream.asInstanceOf[FileInputStream].getChannel()
        val f2 = new FileOutputStream(new File(filePath)).getChannel()
        f.transferTo(0, f.size(), f2)
        f2.close()
        f.close()

        // store metadata to mongo
        storeFileMD(id, filename, contentType, author)
      }
      case None => {
        Logger.error("Could not store file on disk")
        None
      }
    }
  }

  /**
   * Get the bytes of a file from Mongo and the file name.
   */
  override def getBytes(id: UUID): Option[(InputStream, String, String, Long)] = {
    Play.current.configuration.getString("files.path") match {
      case Some(path) => {
        val files = JodaGridFS(FileDAO.dao.collection.db, "uploads")
        files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
          case Some(file) => {
            file.getAs[String]("path") match {
              case Some(relativePath) => {
                val filePath = if (path.last != '/') path + "/" + relativePath else path + relativePath
                Logger.info("Serving file " + filePath)
                Some(new FileInputStream(filePath), 
                    file.getAs[String]("filename").getOrElse("unknown-name"),
                    file.getAs[String]("contentType").getOrElse("unknown"),
                    file.getAs[Long]("length").getOrElse(0))
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

  def generateFileName(id: UUID): Option[String] = {
    Play.current.configuration.getString("files.path") match {
      case Some(path) => {
        var depth = Play.current.configuration.getInt("files.path.depth").getOrElse(3)
        var folder = if (path.last != java.io.File.separatorChar) path + java.io.File.separatorChar else path
        var idstr = id.stringify
        while(depth > 0 && idstr.length > 4) {
          depth -= 1
          folder = folder + java.io.File.separatorChar + idstr.substring(0, 2)
          idstr = idstr.substring(2)
        }
        if (new File(folder).mkdirs()) {
          Some(folder + idstr)
        } else {
          None
        }
      }
      case None => None
    }
  }
}
