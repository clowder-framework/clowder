package repository
import java.io.File
import play.api._
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import com.mongodb.casbah.Imports._
import play.api.Play.current
import se.radley.plugin.salat._
import java.io.InputStream
  
/**
 * Save files on disk.
 * 
 * @author Luigi Marini
 *
 */
trait DiskFileRepositoryComponent {

  val fileRepository: FileRepository
  
  class FileRepository {
    
    def save(inputStream: InputStream, filename: String): String = {
      Play.current.configuration.getString("files.path") match {
        case Some(path) => {
	      val id = UUID.randomUUID().toString()
	      val filePath =  if (path.last != '/') path + "/" + id else path + id
	      Logger.info("Copying file to " + filePath)
	      // FIXME is there a better way than casting to FileInputStream?
	      val f = inputStream.asInstanceOf[FileInputStream].getChannel()
	      val f2 = new FileOutputStream(new File(filePath)).getChannel()
	      f.transferTo(0, f.size(), f2)
	      f2.close()
	      f.close()
	      
	      // store metadata to mongo
	      val files = gridFS("uploads")
	      val mongoFile = files.createFile(Array[Byte]())
	      mongoFile.filename = filename
	      mongoFile.contentType = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
	      mongoFile.put("path", id)
	      mongoFile.save
	      mongoFile.getAs[ObjectId]("_id").get.toString
        }
        case None => {
          Logger.error("Could not store file on disk")
          "ERROR"
        }
      }
    }
    
    def get(id: String): Option[(InputStream, String)] = {
      Play.current.configuration.getString("files.path") match {
        case Some(path) => {
          val files = gridFS("uploads")
          files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
            case Some(file) => {
              file.getAs[String]("path") match {
                case Some(relativePath) => {
                  val filePath =  if (path.last != '/') path + "/" + relativePath else path + relativePath
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
}