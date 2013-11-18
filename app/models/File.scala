/**
 *
 */
package models

import java.util.Date
import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import services.MongoSalatPlugin
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._
import collection.JavaConverters._
import securesocial.core.Identity
import play.api.Logger

/**
 * Uploaded files.
 * 
 * @author Luigi Marini
 *
 */
case class File(
    id: ObjectId = new ObjectId,    
    path: Option[String] = None, 
    filename: String,
    author: Identity,
    uploadDate: Date, 
    contentType: String,
    length: Long = 0,
    showPreviews: String = "DatasetLevel",
    sections: List[Section] = List.empty,
    previews: List[Preview] = List.empty,
    tags: List[String] = List.empty,
    metadata: List[Map[String, Any]] = List.empty,
	thumbnail_id: Option[String] = None,
	isIntermediate: Option[Boolean] = None
)

object FileDAO extends ModelCompanion[File, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[File, ObjectId](collection = x.collection("uploads.files")) {}
  }
  
  def get(id: String): Option[File] = {
    dao.findOneById(new ObjectId(id)) match {
      case Some(file) => {
        val previews = PreviewDAO.findByFileId(file.id)
        val sections = SectionDAO.findByFileId(file.id)
        val sectionsWithPreviews = sections.map { s =>
          val p = PreviewDAO.findOne(MongoDBObject("section_id"->s.id))
          s.copy(preview = p)
        }
        Some(file.copy(sections = sectionsWithPreviews, previews = previews))
      }
      case None => None
    }
  }
  
  
  //Not used yet
  def getMetadata(id: String): scala.collection.immutable.Map[String,Any] = {
		  dao.collection.findOneByID(new ObjectId(id)) match {
		  case None => new scala.collection.immutable.HashMap[String,Any]
		  case Some(x) => {
			  val returnedMetadata = x.getAs[DBObject]("metadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]].toMap
					  returnedMetadata
		  }
	  }
  }
  

  def findByTag(tag: String): List[File] = {
    dao.find(MongoDBObject("tags" -> tag)).toList
  }
  
  def findIntermediates(): List[File] = {
    dao.find(MongoDBObject("isIntermediate" -> true)).toList
  }

  // ---------- Tags related code starts ------------------
  def tag(id: String, tags: List[String]) {
    // TODO: Having issue with "$each", so do multiple updates for now. Improve later.
    // Remove leading and trailing spaces, and reduce multiple continuous spaces to one single space.
    val tags1 = tags.map(t => t.trim().replaceAll("\\s+", " "))
    Logger.debug("tag: tags: " + tags + ".  After removing extra spaces, tags1: " + tags1)
    tags1.foreach(tag => {
      dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("tags" -> tag), false, false, WriteConcern.Safe)
    })
  }

  def removeTags(id: String, tags: List[String]) {
    val tags1 = tags.map(t => t.trim().replaceAll("\\s+", " "))
    Logger.debug("removeTags: tags: " + tags + ".  After removing extra spaces, tags1: " + tags1)
    dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $pullAll("tags" -> tags1), false, false, WriteConcern.Safe)
  }

  def removeAllTags(id: String) {
    dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $set("tags" -> List()), false, false, WriteConcern.Safe)
  }
  // ---------- Tags related code ends ------------------

  def comment(id: String, comment: Comment) {
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("comments" -> Comment.toDBObject(comment)), false, false, WriteConcern.Safe)
  }
  
  def setIntermediate(id: String){
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $set("isIntermediate" -> Some(true)), false, false, WriteConcern.Safe)
  }
  
  def removeFile(id: String){
    dao.findOneById(new ObjectId(id)) match{
      case Some(file) => {
        if(file.isIntermediate.isEmpty){
	        val fileDataset = Dataset.findOneByFileId(file.id)
	        if(!fileDataset.isEmpty){
	        	Dataset.removeFile(fileDataset.get.id.toString(), id)
	        	if(!file.thumbnail_id.isEmpty && !fileDataset.get.thumbnail_id.isEmpty)
		        	if(file.thumbnail_id.get == fileDataset.get.thumbnail_id.get)
		        	  Dataset.newThumbnail(fileDataset.get.id.toString())
	        }         	
	        for(section <- SectionDAO.findByFileId(file.id)){
	          SectionDAO.removeSection(section)
	        }
	        for(preview <- PreviewDAO.findByFileId(file.id)){
	          PreviewDAO.removePreview(preview)
	        }
	        for(comment <- Comment.findCommentsByFileId(id)){
	          Comment.removeComment(comment)
	        }
	        for(texture <- ThreeDTextureDAO.findTexturesByFileId(file.id)){
	          ThreeDTextureDAO.remove(MongoDBObject("_id" -> texture.id))
	        }
	        if(!file.thumbnail_id.isEmpty)
	          Thumbnail.remove(MongoDBObject("_id" -> file.thumbnail_id.get))
        }
        FileDAO.remove(MongoDBObject("_id" -> file.id))
      }
      case None =>
    }    
  }
  
  
}
