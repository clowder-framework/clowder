package models

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import MongoContext.context
import play.api.Play.current
import java.io.InputStream
import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.commons.MongoDBObject
import services.mongodb.MongoSalatPlugin

/**
 * 3D binary geometry files for x3dom.
 * 
 * @author Constantinos Sophocleous
 *
 */
case class ThreeDGeometry (    
	id: ObjectId = new ObjectId,
	file_id: Option[String] = None,
	filename: Option[String] = None,
	contentType: String,
	level: Option[String],
	length: Long
)


object GeometryDAO extends ModelCompanion[ThreeDGeometry, ObjectId]{

   val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[ThreeDGeometry, ObjectId](collection = x.collection("geometries.files")) {}
  }
   
    def findGeometry(fileId: ObjectId, filename: String): Option[ThreeDGeometry] = {    
     try{
    	val theGeometry = dao.find(MongoDBObject("file_id"->fileId, "filename"->filename)).toList.head
    	return Option(theGeometry)
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
      case Some(x) =>  x.gridFS("geometries")
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
    val files = GridFS(SocialUserDAO.dao.collection.db, "geometries")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      case Some(file) => Some(file.inputStream, 
          file.getAs[String]("filename").getOrElse("unknown-name"),
          file.getAs[String]("contentType").getOrElse("unknown"),
          file.getAs[Long]("length").getOrElse(0))
      case None => None
    }
  }
  
}