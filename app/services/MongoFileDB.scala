package services

import java.io.InputStream
import models.File
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import com.mongodb.DBObject
import models.SocialUserDAO
import play.Logger
import play.api.Play.current
import com.mongodb.casbah.gridfs.JodaGridFSDBFile
import models.FileDAO
import java.text.SimpleDateFormat

/**
 * Access file metedata from MongoDB.
 *
 * @author Luigi Marini
 *
 */
trait MongoFileDB {

  /**
   * List all files.
   */
  def listFiles(): List[File] = {
    (for (file <- FileDAO.find(MongoDBObject())) yield file).toList
  }
  
  /**
   * List files after a specified date.
   */
  def listFilesAfter(date: String, limit: Int): List[File] = {
    val order = MongoDBObject("uploadDate"-> -1)
    if (date == "") {
      FileDAO.findAll.sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss").parse(date)
      Logger.info("After " + sinceDate)
      FileDAO.find("uploadDate" $lt sinceDate).sort(order).limit(limit).toList
    }
  }
  
    /**
   * List files before a specified date.
   */
  def listFilesBefore(date: String, limit: Int): List[File] = {
    val order = MongoDBObject("uploadDate"-> -1)
    if (date == "") {
      FileDAO.findAll.sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss").parse(date)
      Logger.info("Before " + sinceDate)
      FileDAO.find("uploadDate" $gt sinceDate).sort(order).limit(limit).toList
    }
  }

  /**
   * Get file metadata.
   */
  def getFile(id: String): Option[File] = {
    FileDAO.findOne(MongoDBObject("_id" -> new ObjectId(id)))
  }

  /**
   * Store file metadata.
   */
  def storeFileMD(id: String, filename: String, contentType: Option[String]): Option[File] = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) =>  x.gridFS("uploads")
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
    val oid = mongoFile.getAs[ObjectId]("_id").get
    
    // FIXME Figure out why SalatDAO has a race condition with gridfs
//    Logger.debug("FILE ID " + oid)
//    val file = FileDAO.findOne(MongoDBObject("_id" -> oid))
//    file match {
//      case Some(id) => Logger.debug("FILE FOUND")
//      case None => Logger.error("NO FILE!!!!!!")
//    }
    
    Some(File(oid, None, mongoFile.filename.get, mongoFile.uploadDate, mongoFile.contentType.get))
  }
}
