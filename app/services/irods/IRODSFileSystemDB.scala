package services.irods

import services._
import javax.inject.Inject
import play.api.Logger
import java.io.InputStream
import com.mongodb.casbah.gridfs.GridFS
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject
import securesocial.core.Identity
import models.UUID
import services.mongodb.{FileDAO, SocialUserDAO, MongoDBFileService, Thumbnail, ThreeDTextureDAO }
import scalax.io._
import scalax.io.Resource

/**
 * Overrides 'save', 'getBytes' and 'removeFiles' of the MongoDBFileService. 
 * 
 * @author Michal Ondrejcek <ondrejce@illinois.edu>
 * @date 2014-09-09
 *    
 */
class IRODSFileSystemDB @Inject() (
  datasets: DatasetService,
  collections: CollectionService,
  sections: SectionService,
  comments: CommentService,
  previews: PreviewService,
  threeD: ThreeDService,
  sparql: RdfSPARQLService) extends MongoDBFileService(datasets,
  collections,
  sections,
  comments,
  previews,
  threeD,
  sparql) {
   
  /**
   * Save a file to iRODS system and store metadata in Mongo. Generates pathID, an id number which is part of the path.
   * It is different from file id stored in MongoDB
   * 
   * @author Michal Ondrejcek <ondrejce@illinois.edu
   * 
   */
  override  def save(inputStream: InputStream, filename: String, contentType: Option[String], author: Identity, showPreviews: String = "DatasetLevel"): Option[models.File] = {
    val pathUUID = UUID.generate
           
    Logger.debug("irods: save() - Filename: " + filename + " pathId: " + pathUUID.toString())
       
    val irw = new IRODSReadWrite
    val fileExist: Boolean = irw.storeFile(pathUUID.toString(), filename, inputStream)

    // store metadata to mongo        
    if (fileExist) {
      storeFileMD(pathUUID, filename, contentType, author)
    } else {
      None
    }
  }
  
  /**
   * Get the info about a file based on file id such as filename from Mongo and file itself from iRods.
   * 
   * @author Michal Ondrejcek <ondrejce@illinois.edu>
   * 
   */
  override def getBytes(id: UUID): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "uploads")
        
    files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
      case Some(file) => {
        val filename: String = file.getAs[String]("filename").getOrElse("unknown-name")
        val pathId: String = file.getAs[String]("path").getOrElse("")

        Logger.debug("irods: getBytes - Serving file: " + filename + " pathId: " + pathId)
                  
        val irw = new IRODSReadWrite                      
        try {
          // last statement = return
          Some(irw.readFile(pathId, filename),
            filename,
            file.getAs[String]("contentType").getOrElse("unknown"),
            file.getAs[Long]("length").getOrElse(0))  
        } 
      }
      case None => None
    }
  }
  
  /**
   * Remove file fromIRODS and matadata from Mongo.
   *
   * @author Michal Ondrejcek <ondrejce@illinois.edu>
   * 
   * 
   */
  override def removeFile(id: UUID) = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "uploads")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
      case Some(file) => {
        val filename: String = file.getAs[String]("filename").getOrElse("unknown-name")
        val pathId: String = file.getAs[String]("path").getOrElse("")        
        
        Logger.debug("irods: removeFile() - Filename: " + filename + " pathId: " + pathId)
            
        val irw = new IRODSReadWrite
        val success: Boolean = irw.deleteFile(pathId, filename)
        Logger.info("irods: File " + filename + " deleted? " + success)
        
      }
      case None => None
    }
    
    // no change from MongoDBFileService
    get(id) match{
      case Some(file) => {
        if(file.isIntermediate.isEmpty){
          val fileDatasets = datasets.findByFileId(file.id)
          for(fileDataset <- fileDatasets){
            datasets.removeFile(fileDataset.id, id)
            if(!file.xmlMetadata.isEmpty){
              datasets.index(fileDataset.id)
            }
            if(!file.thumbnail_id.isEmpty && !fileDataset.thumbnail_id.isEmpty)
              if(file.thumbnail_id.get == fileDataset.thumbnail_id.get){
                datasets.newThumbnail(fileDataset.id)	        	  
		        	}           
          }
          for(section <- sections.findByFileId(file.id)){
            sections.removeSection(section)
          }
          for(preview <- previews.findByFileId(file.id)){
            previews.removePreview(preview)
          }
          for(comment <- comments.findCommentsByFileId(id)){
            comments.removeComment(comment)
          }
          for(texture <- threeD.findTexturesByFileId(file.id)){
            ThreeDTextureDAO.removeById(new ObjectId(texture.id.stringify))
          }
          if(!file.thumbnail_id.isEmpty)
            Thumbnail.removeById(new ObjectId(file.thumbnail_id.get))
        }
        FileDAO.removeById(new ObjectId(file.id.stringify))
      }
      case None => Logger.debug("irods: removeFiles() - File not found")
    }
  }
}