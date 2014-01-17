package services

import java.io.InputStream
import models._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import com.mongodb.DBObject
import play.api.Play.current
import com.mongodb.casbah.gridfs.JodaGridFSDBFile
import java.text.SimpleDateFormat
import securesocial.core.Identity
import com.novus.salat._
import com.novus.salat.global._
import java.util.Date
import java.util.Calendar
import scala.collection.mutable.ListBuffer
import scala.util.parsing.json.JSONArray
import play.api.Logger
import scala.util.parsing.json.JSONArray
import scala.Some
import models.File

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
      FileDAO.find("isIntermediate" $ne true).sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.info("After " + sinceDate)
      FileDAO.find($and("isIntermediate" $ne true, "uploadDate" $lt sinceDate)).sort(order).limit(limit).toList
    }
  }
  
    /**
   * List files before a specified date.
   */
  def listFilesBefore(date: String, limit: Int): List[File] = {
    var order = MongoDBObject("uploadDate"-> -1)
    if (date == "") {
      FileDAO.find("isIntermediate" $ne true).sort(order).limit(limit).toList
    } else {
      order = MongoDBObject("uploadDate"-> 1) 
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.info("Before " + sinceDate)
      var fileList = FileDAO.find($and("isIntermediate" $ne true, "uploadDate" $gt sinceDate)).sort(order).limit(limit + 1).toList.reverse
      fileList = fileList.filter(_ != fileList.last)
      fileList      
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
  def storeFileMD(id: String, filename: String, contentType: Option[String], author: Identity): Option[File] = {
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
    mongoFile.put("author", SocialUserDAO.toDBObject(author))
    mongoFile.save
    val oid = mongoFile.getAs[ObjectId]("_id").get
    
    // FIXME Figure out why SalatDAO has a race condition with gridfs
//    Logger.debug("FILE ID " + oid)
//    val file = FileDAO.findOne(MongoDBObject("_id" -> oid))
//    file match {
//      case Some(id) => Logger.debug("FILE FOUND")
//      case None => Logger.error("NO FILE!!!!!!")
//    }
    
    Some(File(oid, None, mongoFile.filename.get, author, mongoFile.uploadDate, mongoFile.contentType.get, mongoFile.length))
  }

  def index(id: String) {
    getFile(id) match {
      case Some(file) => {
        var tagListBuffer = new ListBuffer[String]()

        for (tag <- file.tags){
          tagListBuffer += tag.name
        }

        val tagsJson = new JSONArray(tagListBuffer.toList)

        Logger.debug("tagStr=" + tagsJson);

        val comments = for(comment <- Comment.findCommentsByFileId(id)) yield {
          comment.text
        }
        val commentJson = new JSONArray(comments)

        Logger.debug("commentStr=" + commentJson.toString())

        val usrMd = FileDAO.getUserMetadataJSON(id)
        Logger.debug("usrmd=" + usrMd)

        val techMd = FileDAO.getTechnicalMetadataJSON(id)
        Logger.debug("techmd=" + techMd)

        val xmlMd = FileDAO.getXMLMetadataJSON(id)
        Logger.debug("xmlmd=" + xmlMd)

        var fileDsId = ""
        var fileDsName = ""

        for(dataset <- Dataset.findByFileId(file.id)){
          fileDsId = fileDsId + dataset.id.toString + "  "
          fileDsName = fileDsName + dataset.name + "  "
        }

        current.plugin[ElasticsearchPlugin].foreach {
          _.index("data", "file", id,
            List(("filename", file.filename), ("contentType", file.contentType),("datasetId",fileDsId),("datasetName",fileDsName), ("tag", tagsJson.toString), ("comments", commentJson.toString), ("usermetadata", usrMd), ("technicalmetadata", techMd), ("xmlmetadata", xmlMd)))
        }
      }
      case None => Logger.error("File not found: " + id)
    }
  }
}
