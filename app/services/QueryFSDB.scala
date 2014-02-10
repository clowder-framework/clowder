package services

import models.TempFile
import java.io.InputStream
import play.api.Play
import play.api.Play.current
import java.util.UUID
import play.Logger
import java.io.FileInputStream
import java.io.FileOutputStream
import com.mongodb.casbah.gridfs.JodaGridFS
import models.TempFileDAO
import org.bson.types.ObjectId
import java.io.File
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import com.mongodb.DBObject
import models.SocialUserDAO
import com.mongodb.casbah.gridfs.JodaGridFSDBFile
import java.text.SimpleDateFormat
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.GridFS

trait QueryFSDB{
   
/*def save(inputStream: InputStream, filename: String, contentType: Option[String]): Option[models.TempFile] = {
    Play.current.configuration.getString("queries.path") match {
      case Some(path) => {
        val id1 = UUID.randomUUID().toString()
        val id=new ObjectId(id1)
        val filePath = if (path.last != '/') path + "/" + id else path + id
        Logger.info("Copying file to " + filePath)
        // FIXME is there a better way than casting to FileInputStream?
        val f = inputStream.asInstanceOf[FileInputStream].getChannel()
        val f2 = new FileOutputStream(new File(filePath)).getChannel()
        f.transferTo(0, f.size(), f2)
        f2.close()
        f.close()
        Logger.info("id="+id+"  filename="+filename+" contentType="+ contentType)
        // store metadata to mongo
        storeFileMD(id.toString(), filename, contentType)
      }
      case None => {
        Logger.error("Could not store file on disk")
        None
      }
    }
  }

def get(id: String): Option[(InputStream, String, String, Long)]={
  Play.current.configuration.getString("queries.path") match {
      case Some(path) => {
        val queries = JodaGridFS(TempFileDAO.dao.collection.db, "uploadquery")
        queries.findOne(MongoDBObject("id" -> new ObjectId(id))) match {
          case Some(query) => {
                 query.getAs[String]("path") match {
              case Some(relativePath) => {
                val filePath = if (path.last != '/') path + "/" + relativePath else path + relativePath
                Logger.info("Serving file " + filePath)
                Some(new FileInputStream(filePath), 
                    query.getAs[String]("filename").getOrElse("unknown-name"),
                    query.getAs[String]("contentType").getOrElse("unknown"),
                    query.getAs[Long]("length").getOrElse(0))
              }
              case None => None
            }
          }
          case None => None
        }
      }
      case None => None
    }
  }
*/
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
