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
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.content.StringBody
import java.nio.charset.Charset
import org.apache.http.util.EntityUtils
import java.io.BufferedReader
import java.io.InputStreamReader

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
	length: Long,
	extractor_id: Option[String] = None,
	iipURL: Option[String] = None,
	iipImage: Option[String] = None,
	iipKey: Option[String] = None		
)

object PreviewDAO extends ModelCompanion[Preview, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Preview, ObjectId](collection = x.collection("previews.files")) {}
  }
  
  def setIIPReferences(id: String, iipURL: String, iipImage: String, iipKey: String){
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $set("iipURL" -> Some(iipURL), "iipImage" -> Some(iipImage), "iipKey" -> Some(iipKey)), false, false, WriteConcern.Safe)
  }
  
  def findByFileId(id: ObjectId): List[Preview] = {
    dao.find(MongoDBObject("file_id"->id)).toList
  }
  
  def findBySectionId(id: ObjectId): List[Preview] = {
    dao.find(MongoDBObject("section_id"->id)).toList
  }
  
  def findByDatasetId(id: ObjectId): List[Preview] = {
    dao.find(MongoDBObject("dataset_id"->id)).toList
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
    for(tile <- TileDAO.findByPreviewId(p.id)){
	          TileDAO.remove(MongoDBObject("_id" -> tile.id))
	        }
    // for IIP server references, also delete the files being referenced on the IIP server they reside
    if(!p.iipURL.isEmpty){
      val httpclient = new DefaultHttpClient()
      val httpPost = new HttpPost(p.iipURL.get+"/deleteFile.php")
      val entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)
      entity.addPart("key", new StringBody(p.iipKey.get, "text/plain",
                Charset.forName( "UTF-8" )))
      entity.addPart("file", new StringBody(p.iipImage.get, "text/plain",
                Charset.forName( "UTF-8" )))
      httpPost.setEntity(entity)
      val imageUploadResponse = httpclient.execute(httpPost)
      Logger.info(imageUploadResponse.getStatusLine().toString())
      
      val dirEntity = imageUploadResponse.getEntity()
      Logger.info("IIP server: " + EntityUtils.toString(dirEntity))
    }
    
    if(!p.filename.isEmpty)
      // for oni previews, read the ONI frame references from the preview file and remove them
      if(p.filename.get.endsWith(".oniv")){
    	  	  val theFile = getBlob(p.id.toString()) 
    	  	  val frameRefReader = new BufferedReader(new InputStreamReader(theFile.get._1))
    	  	  var fileData = new StringBuilder()
    	  	  var currLine = frameRefReader.readLine()
    	  	  while(currLine != null) {
    	  		  fileData.append(currLine)
    	  		  currLine = frameRefReader.readLine()
    	  	  }
    	  	  frameRefReader.close()
    	  	  val frames = fileData.toString().split(",",-1)
    	  	  var i = 0
    	  	  for(i <- 0 to frames.length - 2){
    	  	    PreviewDAO.remove(MongoDBObject("_id" -> new ObjectId(frames(i))))
    	  	  }
      //same for PTM file map references 
      }else if(p.filename.get.endsWith(".ptmmaps")){
    	  	  val theFile = getBlob(p.id.toString()) 
    	  	  val frameRefReader = new BufferedReader(new InputStreamReader(theFile.get._1))
    	  	  var currLine = frameRefReader.readLine()
    	  	  while(currLine != null) {
    	  	      if(!currLine.equals(""))
    	  	    	  PreviewDAO.remove(MongoDBObject("_id" -> new ObjectId(currLine.substring(currLine.indexOf(": ")+2))))
    	  		  currLine = frameRefReader.readLine()
    	  	  }
    	  	  frameRefReader.close()       
      }
    
    PreviewDAO.remove(MongoDBObject("_id" -> p.id))    
  }
  
  
  
}





