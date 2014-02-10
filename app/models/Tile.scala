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
 * Pyramid tiles of images for Seadragon.
 * 
 * @author Constantinos Sophocleous
 *
 */
case class Tile (    
	id: ObjectId = new ObjectId,
	preview_id: Option[String] = None,
	filename: Option[String] = None,
	contentType: String,
	level: Option[String],
	length: Long
)

object TileDAO extends ModelCompanion[Tile, ObjectId] {
  
   val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Tile, ObjectId](collection = x.collection("tiles.files")) {}
  }
   
   def findTile(previewId: ObjectId, filename: String, level: String): Option[Tile] = {    
     try{
    	val theTile = dao.find(MongoDBObject("preview_id"->previewId, "filename"->filename, "level"->level)).toList.head
    	return Option(theTile)
     } catch{
       case e:NoSuchElementException => return None 
     }   
  }
  def findByPreviewId(previewId: ObjectId): List[Tile] = {
    dao.find(MongoDBObject("preview_id" -> previewId)).toList
  } 
   
     /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) =>  x.gridFS("tiles")
    }
    val mongoFile = files.createFile(inputStream)
//    Logger.debug("Uploading file " + filename)
    mongoFile.filename = filename
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.contentType = ct
    mongoFile.save
    mongoFile.getAs[ObjectId]("_id").get.toString
  }
  
  /**
   * Get blob.
   */
  def getBlob(id: String): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "tiles")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      case Some(file) => Some(file.inputStream, 
          file.getAs[String]("filename").getOrElse("unknown-name"),
          file.getAs[String]("contentType").getOrElse("unknown"),
          file.getAs[Long]("length").getOrElse(0))
      case None => None
    }
  }
  
  
  

}  
