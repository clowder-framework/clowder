package models

import services.MongoSalatPlugin
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import MongoContext.context
import play.api.Play.current
import java.io.InputStream
import play.api.Logger
import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.commons.MongoDBObject

/**
 * 3D textures for x3dom generated from obj models.
 * 
 * @author Constantinos Sophocleous
 *
 */
case class ThreeDTexture (    
	id: ObjectId = new ObjectId,
	file_id: Option[String] = None,
	filename: Option[String] = None,
	contentType: String,
	length: Long
)


object ThreeDTextureDAO extends ModelCompanion[ThreeDTexture, ObjectId]{

	val dao = current.plugin[MongoSalatPlugin] match {
		case None    => throw new RuntimeException("No MongoSalatPlugin");
		case Some(x) =>  new SalatDAO[ThreeDTexture, ObjectId](collection = x.collection("textures.files")) {}
	}
	
	def findTexture(fileId: ObjectId, filename: String): Option[ThreeDTexture] = {    
			try{
				val theTexture = dao.find(MongoDBObject("file_id"->fileId, "filename"->filename)).toList.head
						return Option(theTexture)
			} catch{
			case e:NoSuchElementException => return None 
			}   
	}
		    
	/**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) =>  x.gridFS("textures")
    }
    val mongoFile = files.createFile(inputStream)
//    Logger.debug("Uploading file " + filename)
    mongoFile.filename = filename
    var ct = play.api.http.ContentTypes.BINARY
    mongoFile.contentType = ct
    mongoFile.save
    mongoFile.getAs[ObjectId]("_id").get.toString
  }
  
   /**
   * Get blob.
   */
  def getBlob(id: String): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "textures")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      case Some(file) => Some(file.inputStream, 
          file.getAs[String]("filename").getOrElse("unknown-name"),
          file.getAs[String]("contentType").getOrElse("unknown"),
          file.getAs[Long]("length").getOrElse(0))
      case None => None
    }
  }
  
}