package services.irods

import services.mongodb.MongoDBFileService
import services._
import javax.inject.Inject
import play.api.{ Play, Logger }
import play.api.Play.current
import java.io.{ File, InputStream, FileInputStream, FileOutputStream }
import com.mongodb.casbah.gridfs.GridFS
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject
import securesocial.core.Identity
import models.UUID
import services.mongodb.{FileDAO, SocialUserDAO, MongoDBFileService, Thumbnail, ThreeDTextureDAO }
import scalax.io._
import scalax.io.Resource
import scalax.file.Path

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
   * Save a file to irods system and store metadata in Mongo.
   */
  override  def save(inputStream: InputStream, filename: String, contentType: Option[String], author: Identity, showPreviews: String = "DatasetLevel"): Option[models.File] = {
    Play.current.configuration.getString("datastorage.active") match {
      case irods => {
        val id = UUID.generate
           
        Logger.debug("irods: Filename: " + filename)
        
        val pathFolder = ("/Users/mo/Public/medici")
        val path: Path = Path.fromString(pathFolder)
        path.createDirectory(failIfExists=false)
        //using Commons IO:
        val outputStream = new FileOutputStream(pathFolder + "/" + filename)
        org.apache.commons.io.IOUtils.copy(inputStream, outputStream)
        outputStream.close()
        
        //var irw = new IRODSReadWrite
        //var sf = irw.storeFile(id.toString(), filename, inputStream)

        // store metadata to mongo        
        //TODO
        // I want to store path to and in irods as well to the File.path currently not used, it needs nee metadata
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
   */
  override def getBytes(id: UUID): Option[(InputStream, String, String, Long)] = {
    Play.current.configuration.getString("datastorage.active") match {
      case irods => {

       val files = GridFS(SocialUserDAO.dao.collection.db, "uploads")
        
       files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
         case Some(file) => {
           val gridInputStream = file.inputStream
           Logger.debug("irods: Grid Input stream: " + gridInputStream.available)
           val filename: String = file.getAs[String]("filename").getOrElse("unknown-name")
           
           //TODO
           // implement the path from metadata, it can be used in connect for example effectively bypassing the config irods server specs
           //val path: String = file.getAs[String]("path").getOrElse("unknown-path")
           val path = "unknown-path"

           Logger.debug("irods: Serving file: " + filename)
                  
          // var irw = new IRODSReadWrite
           //var is: InputStream = irw.readFile(id.toString(), filename, path)
           
           //val is:InputStream = new FileInputStream("/Users/mo/Public/test_dir/telefony.txt")
           val is:InputStream = new FileInputStream("/Users/mo/Public/test_dir/" + filename)
           
           //val irt = new IRODSTest
           //val is:InputStream = irt.inputStreamReadFile("/Users/mo/Public/test_dir/telefony.txt")
           //irt.inputStreamRead(is);//if( is!=null ) is.close()
           
           Logger.debug("irods: Input stream: " + is.available)
           
           try {
           // last statement = return
           Some(is, 
                filename,
                file.getAs[String]("contentType").getOrElse("unknown"),
                file.getAs[Long]("length").getOrElse(0))  
           } finally {
            // if( is!=null ) is.close()
         }
          }
          case None => None
        }
      }
      case None => None
    }
  }
  
  /**
   * Remove file fromIRODS and matadata from Mongo(?).
   */
  override def removeFile(id: UUID) = {

    val files = GridFS(SocialUserDAO.dao.collection.db, "uploads")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
      case Some(file) => {
        val filename: String = file.getAs[String]("filename").getOrElse("unknown-name")
        
        Logger.debug("irods: Filename: " + filename)
        
        val pathFolder = ("/Users/mo/Public/medici")
        val path: Path = Path.fromString(pathFolder + "/" + filename)
        path.deleteIfExists()
        Logger.debug("irods: File: " + filename + " has been deleted")
      }
    }
    
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
      case None => Logger.debug("File not found")
    }
  }
}