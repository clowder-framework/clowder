package services.mongodb

import services.ThreeDService
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject
import java.io.InputStream
import play.api.Play._
import scala.Some
import com.mongodb.casbah.gridfs.GridFS
import models._
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import java.io.InputStream
import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.commons.MongoDBObject
import services.mongodb.MongoSalatPlugin
import com.mongodb.casbah.Imports._
import scala.Some
import com.mongodb.WriteConcern
import play.api.libs.json.JsValue
import scala.Some
import models.ThreeDTexture
import scala.Some
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import MongoContext.context
import play.api.Play.current
import java.io.InputStream
import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.commons.MongoDBObject
import services.mongodb.MongoSalatPlugin
import models.ThreeDGeometry
import models.ThreeDTexture
import scala.Some
import models.ThreeDGeometry
import models.ThreeDTexture
import scala.Some

/**
 * Created by lmarini on 2/26/14.
 */
class MongoDBThreeDService extends ThreeDService {

  def getTexture(textureId: String): Option[ThreeDTexture] ={
    ThreeDTextureDAO.findOneById(new ObjectId(textureId))
  }

  def findTexture(fileId: String, filename: String): Option[ThreeDTexture] = {
    try {
      val theTexture = ThreeDTextureDAO.find(MongoDBObject("file_id" -> new ObjectId(fileId), "filename" -> filename)).toList.head
      return Option(theTexture)
    } catch {
      case e: NoSuchElementException => return None
    }
  }

  def findTexturesByFileId(fileId: String): List[ThreeDTexture] = {
    ThreeDTextureDAO.find(MongoDBObject("file_id" -> new ObjectId(fileId))).toList
  }

  def updateTexture(fileId: String, textureId: String, fields: Seq[(String, JsValue)]) {
    val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
    ThreeDTextureDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(textureId)),
      $set("metadata" -> metadata, "file_id" -> new ObjectId(fileId)), false, false, WriteConcern.SAFE)
  }

  def updateGeometry(fileId: String, geometryId: String, fields: Seq[(String, JsValue)]) {
    val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
    GeometryDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(geometryId)),
      $set("metadata" -> metadata, "file_id" -> new ObjectId(fileId)), false, false, WriteConcern.SAFE)
  }

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => x.gridFS("textures")
    }
    val mongoFile = files.createFile(inputStream)
    //    Logger.debug("Uploading file " + filename)
    mongoFile.filename = filename
    var ct = play.api.http.ContentTypes.BINARY
    mongoFile.contentType = ct
    mongoFile.save
    mongoFile.getAs[ObjectId]("_id").get.toString
  }

  /**
   * Get blob.
   */
  def getBlob(id: String): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "textures")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      case Some(file) => Some(file.inputStream,
        file.getAs[String]("filename").getOrElse("unknown-name"),
        file.getAs[String]("contentType").getOrElse("unknown"),
        file.getAs[Long]("length").getOrElse(0))
      case None => None
    }
  }

  def findGeometry(fileId: String, filename: String): Option[ThreeDGeometry] = {
    try {
      val theGeometry = GeometryDAO.find(MongoDBObject("file_id" -> new ObjectId(fileId), "filename" -> filename)).toList.head
      return Option(theGeometry)
    } catch {
      case e: NoSuchElementException => return None
    }
  }

  /**
   * Save blob.
   */
  def saveGeometry(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => x.gridFS("geometries")
    }
    val mongoFile = files.createFile(inputStream)
    mongoFile.filename = filename
    var ct = play.api.http.ContentTypes.BINARY
    mongoFile.contentType = ct
    mongoFile.save
    mongoFile.getAs[ObjectId]("_id").get.toString
  }

  def getGeometry(id: String): Option[ThreeDGeometry] = {
    GeometryDAO.findOneById(new ObjectId(id))
  }

  /**
   * Get blob.
   */
  def getGeometryBlob(id: String): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "geometries")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      case Some(file) => Some(file.inputStream,
        file.getAs[String]("filename").getOrElse("unknown-name"),
        file.getAs[String]("contentType").getOrElse("unknown"),
        file.getAs[Long]("length").getOrElse(0))
      case None => None
    }
  }

}

object ThreeDTextureDAO extends ModelCompanion[ThreeDTexture, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ThreeDTexture, ObjectId](collection = x.collection("textures.files")) {}
  }
}

object GeometryDAO extends ModelCompanion[ThreeDGeometry, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ThreeDGeometry, ObjectId](collection = x.collection("geometries.files")) {}
  }
}

object ThreeDAnnotation extends ModelCompanion[ThreeDAnnotation, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ThreeDAnnotation, ObjectId](collection = x.collection("previews.files.annotations")) {}
  }
}
