/**
 *
 */
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
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._

/**
 * Preview bytes and metadata.
 * 
 * @author Luigi Marini
 *
 */
case class Preview (
	id: ObjectId = new ObjectId,
	file_id: Option[String] = None,
	section_id: Option[String] = None,
	dataset_id: Option[String] = None,
	filename: Option[String] = None,
	contentType: String,
	annotations: List[ThreeDAnnotation] = List.empty,
	length: Long
		
)

object PreviewDAO extends ModelCompanion[Preview, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Preview, ObjectId](collection = x.collection("previews.files")) {}
  }
  
  def findByFileId(id: ObjectId): List[Preview] = {
    dao.find(MongoDBObject("file_id"->id)).toList
  }
  
  def findBySectionId(id: ObjectId): List[Preview] = {
    dao.find(MongoDBObject("section_id"->id)).toList
  }
  
    /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) =>  x.gridFS("previews")
    }
    val mongoFile = files.createFile(inputStream)
    Logger.debug("Uploading file " + filename)
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
    val files = GridFS(SocialUserDAO.dao.collection.db, "previews")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      case Some(file) => Some(file.inputStream, 
          file.getAs[String]("filename").getOrElse("unknown-name"),
          file.getAs[String]("contentType").getOrElse("unknown"),
          file.getAs[Long]("length").getOrElse(0))
      case None => None
    }
  }
  
  /**
   * Add annotation to 3D model preview.
   */
   def annotation(id: String, annotation: ThreeDAnnotation) {
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("annotations" -> ThreeDAnnotation.toDBObject(annotation)), false, false, WriteConcern.Safe)
  }
   
  def findAnnotation(preview_id: String, x_coord: String, y_coord: String, z_coord: String): Option[ThreeDAnnotation] = {
       dao.findOneById(new ObjectId(preview_id)) match{  
    	   case Some(preview) => {
    	      for(annotation <- preview.annotations){
    	    	  if(annotation.x_coord.equals(x_coord) && annotation.y_coord.equals(y_coord) && annotation.z_coord.equals(z_coord))
    	    		  return Option(annotation)
    	      }        
    	      return None
    	   }
    	   case None => return None
       }         
  }
  
  def updateAnnotation(preview_id: String, annotation_id: String, description: String){      
    dao.findOneById(new ObjectId(preview_id)) match{  
    	   case Some(preview) => {
    	      //var newAnnotations = List.empty[ThreeDAnnotation]
    	      for(annotation <- preview.annotations){
    	    	  if(annotation.id.toString().equals(annotation_id)){	
    	    	    update(MongoDBObject("_id" -> new ObjectId(preview_id), "annotations._id" -> annotation.id) , $set("annotations.$.description" -> description), false, false, WriteConcern.Safe)
    	    	    return
    	    	  }
    	      }	          	      
    	      return
    	   }
    	   case None => return
    } 
  }
  
   
  def listAnnotations(preview_id: String): List[ThreeDAnnotation] = {
       dao.findOneById(new ObjectId(preview_id)) match{
    	   case Some(preview) => {
    		   return preview.annotations
    	   }
    	   case None => return List.empty
       }
  }
  
  def removePreview(p: Preview){
    PreviewDAO.remove(p)    
  }
  
}





