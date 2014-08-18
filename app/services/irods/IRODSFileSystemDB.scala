package services.irods

import services.mongodb.MongoDBFileService
import services._
import javax.inject.Inject
import play.api.{ Play, Logger }
import play.api.Play.current
import java.io.{ File, InputStream, FileInputStream, FileOutputStream, ByteArrayInputStream }
import com.mongodb.casbah.gridfs.GridFS
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject
import securesocial.core.Identity
import models.UUID
import services.mongodb.{FileDAO, SocialUserDAO, MongoDBFileService, Thumbnail, ThreeDTextureDAO }
import scalax.io._
import scalax.io.Resource
import scalax.file.Path

/**
 * Overrides 'save', 'getBytes' and 'removeFiles' of the MongoDBFileService. 
 * 
 * @author Michal Ondrejcek <ondrejce@illinois.edu>
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
  
  val ipg = Play.application.plugin[IRODSPlugin].getOrElse(throw new RuntimeException("irods: Plugin not loaded."))
  
  /**
   * Save a file to iRODS system and store metadata in Mongo.
   * 
   * @author Michal Ondrejcek <ondrejce@illinois.edu>
   * 
   */
  override  def save(inputStream: InputStream, filename: String, contentType: Option[String], author: Identity, showPreviews: String = "DatasetLevel"): Option[models.File] = {
    Play.current.configuration.getString("datastorage.active") match {
      case irods => {
        val id = UUID.generate
           
        Logger.debug("irods: save() - Filename: " + filename + " id: " + id.toString())
       
        val irw = new IRODSReadWrite
        val sf = irw.storeFile(id.toString(), filename, inputStream)

        // store metadata to mongo        
        //TODO
        // I want to store path to and ifrom irods to the File.path
        // def storeFileMD(id: UUID, filename: String, contentType: Option[String], author: Identity, path: String): Option[File] = {
        storeFileMD(id, filename, contentType, author)
        
      }
      case _ => {
        Logger.error("irods: Could not store file on IRODS")
        None
      }
    }
  }
  
  /**
   * Get the info about a file based on id such as filename from Mongo and file itself from iRods.
   * 
   * @author Michal Ondrejcek <ondrejce@illinois.edu>
   * 
   */
  override def getBytes(id: UUID): Option[(InputStream, String, String, Long)] = {
    Play.current.configuration.getString("datastorage.active") match {
      case irods => {

       val files = GridFS(SocialUserDAO.dao.collection.db, "uploads")
        
       files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
         case Some(file) => {
           val filename: String = file.getAs[String]("filename").getOrElse("unknown-name")
           
           Logger.debug("irods: getBytes - Serving file: " + filename)
                  
           val irw = new IRODSReadWrite
           val is: InputStream = irw.readFile(id.toString(), filename)
           
           // HACK with an intermediary buffer - works, no errors
           // I could not pass the InputStream is directly to Some(InputStream, String, String, Long) because, 
           // I think the is.close does not propagate from File > downloads > Enumerator through the chain
           // to the IRODSFileInputStream. Closing the stream here works.
           val buffer = Array.ofDim[Byte](10240)
           is.read(buffer)
           is.close()
                     
           try {
             // last statement = return
             Some(new ByteArrayInputStream(buffer),
                filename,
                file.getAs[String]("contentType").getOrElse("unknown"),
                file.getAs[Long]("length").getOrElse(0))  
           } finally {
            ipg.closeIRODSConnection()    
         }
          }
          case None => None
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
        
        Logger.debug("irods: removeFile() - Filename: " + filename)
            
        val irw = new IRODSReadWrite
        val success: Boolean = irw.deleteFile(id.toString(), filename)
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