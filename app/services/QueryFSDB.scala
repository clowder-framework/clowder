package services

import models.TempFile
import java.io.InputStream
import play.api.Play.current
import play.Logger
import models.TempFileDAO
import com.mongodb.casbah.commons.MongoDBObject
import models.SocialUserDAO
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.GridFS
import services.mongodb.MongoSalatPlugin

trait QueryFSDB{

  def save(inputStream: InputStream, filename: String, contentType: Option[String]): Option[TempFile] = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) =>  x.gridFS("uploadquery")
    }
    
    // required to avoid race condition on save
    files.db.setWriteConcern(WriteConcern.Safe)
    
    val mongoFile = files.createFile(inputStream)
    Logger.debug("Uploading file " + filename)
    mongoFile.filename = filename
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.contentType = ct
    mongoFile.save
    val oid = mongoFile.getAs[ObjectId]("_id").get
    Some(TempFile(oid, None, mongoFile.filename.get, mongoFile.uploadDate, mongoFile.contentType.get, mongoFile.length))
  }
  
  
   
  
   def get(id: String): Option[(InputStream, String, String, Long)] = {
    
     val queries = GridFS(SocialUserDAO.dao.collection.db, "uploadquery")
    
      queries.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      
      case Some(query) => {
        //Logger.debug(query.toString())
        Logger.debug("get: file name: "+query.filename +" Query id ="+ query.id.toString())
        Some(query.inputStream, 
          query.getAs[String]("filename").getOrElse("unknown-name"), 
          query.getAs[String]("contentType").getOrElse("unknown"),
          query.getAs[Long]("length").getOrElse(0))
          }
      case None => None
    }
  }
  
def listFiles(): List[TempFile]={
  (for (file <- TempFileDAO.find(MongoDBObject())) yield file).toList
  }
  
  
  /**
   * Get file metadata.
   */
def getFile(id: String): Option[TempFile] = {
    TempFileDAO.findOne(MongoDBObject("_id" -> new ObjectId(id)))
  }

  /**
   * Store file metadata.
   */
  def storeFileMD(id: String, filename: String, contentType: Option[String]): Option[TempFile]={
     val files = current.plugin[MongoSalatPlugin] match {
      case None    => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) =>  x.gridFS("uploadquery")
    }
    
    // required to avoid race condition on save
    files.db.setWriteConcern(WriteConcern.Safe)
    
    val mongoFile = files.createFile(Array[Byte]())
    mongoFile.filename = filename
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.contentType = ct
    mongoFile.put("path", id)
    mongoFile.save
    val oid = mongoFile.getAs[ObjectId]("_id").get ///getting object id
    //mongoFile._id
    Logger.debug("StoreMD id="+ oid)
     Some(TempFile(oid, None, mongoFile.filename.get, mongoFile.uploadDate, mongoFile.contentType.get, mongoFile.length))
    
  }

}
