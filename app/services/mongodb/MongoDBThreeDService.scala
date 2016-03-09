package services.mongodb

import services.{ByteStorageService, ThreeDService}
import models._
import com.mongodb.casbah.Imports._
import com.mongodb.WriteConcern
import play.api.libs.json.JsValue
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import MongoContext.context
import play.api.Play.current
import java.io.InputStream
import com.mongodb.casbah.commons.MongoDBObject
import models.ThreeDGeometry
import models.ThreeDTexture
import util.FileUtils

class MongoDBThreeDService extends ThreeDService {

  def getTexture(textureId: UUID): Option[ThreeDTexture] ={
    ThreeDTextureDAO.findOneById(new ObjectId(textureId.stringify))
  }

  def findTexture(fileId: UUID, filename: String): Option[ThreeDTexture] = {
    try {
      val theTexture = ThreeDTextureDAO.find(MongoDBObject("file_id" -> new ObjectId(fileId.stringify), "filename" -> filename)).toList.head
      return Option(theTexture)
    } catch {
      case e: NoSuchElementException => return None
    }
  }

  def findTexturesByFileId(fileId: UUID): List[ThreeDTexture] = {
    ThreeDTextureDAO.find(MongoDBObject("file_id" -> new ObjectId(fileId.stringify))).toList
  }

  def updateTexture(fileId: UUID, textureId: UUID, fields: Seq[(String, JsValue)]) {
    val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
    ThreeDTextureDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(textureId.stringify)),
      $set("metadata" -> metadata, "file_id" -> new ObjectId(fileId.stringify)), false, false, WriteConcern.SAFE)
  }

  def updateGeometry(fileId: UUID, geometryId: UUID, fields: Seq[(String, JsValue)]) {
    val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
    GeometryDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(geometryId.stringify)),
      $set("metadata" -> metadata, "file_id" -> new ObjectId(fileId.stringify)), false, false, WriteConcern.SAFE)
  }

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    ByteStorageService.save(inputStream, ThreeDTextureDAO.COLLECTION) match {
      case Some(x) => {
        val text = ThreeDTexture(UUID.generate(), x._1, x._2, None, Some(filename), FileUtils.getContentType(filename, contentType), x._4)
        ThreeDTextureDAO.save(text)
        text.id.stringify
      }
      case None => ""
    }
  }

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    getTexture(id).flatMap { x =>
      ByteStorageService.load(x.loader, x.loader_id, ThreeDTextureDAO.COLLECTION).map((_, x.filename.getOrElse(""), x.contentType, x.length))
    }
  }

  def findGeometry(fileId: UUID, filename: String): Option[ThreeDGeometry] = {
    try {
      val theGeometry = GeometryDAO.find(MongoDBObject("file_id" -> new ObjectId(fileId.stringify), "filename" -> filename)).toList.head
      return Option(theGeometry)
    } catch {
      case e: NoSuchElementException => return None
    }
  }

  /**
   * Save blob.
   */
  def saveGeometry(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    ByteStorageService.save(inputStream, GeometryDAO.COLLECTION) match {
      case Some(x) => {
        val geom = ThreeDGeometry(UUID.generate(), x._1, x._2, None, Some(filename), FileUtils.getContentType(filename, contentType), None, x._4)
        GeometryDAO.save(geom)
        geom.id.stringify
      }
      case None => ""
    }
  }

  def getGeometry(id: UUID): Option[ThreeDGeometry] = {
    GeometryDAO.findOneById(new ObjectId(id.stringify))
  }

  /**
   * Get blob.
   */
  def getGeometryBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    getGeometry(id).flatMap { x =>
      ByteStorageService.load(x.loader, x.loader_id, GeometryDAO.COLLECTION).map((_, x.filename.getOrElse(""), x.contentType, x.length))
    }
  }

}

object ThreeDTextureDAO extends ModelCompanion[ThreeDTexture, ObjectId] {
  val COLLECTION = "textures"

  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ThreeDTexture, ObjectId](collection = x.collection(COLLECTION)) {}
  }
}

object GeometryDAO extends ModelCompanion[ThreeDGeometry, ObjectId] {
  val COLLECTION = "geometries"

  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ThreeDGeometry, ObjectId](collection = x.collection(COLLECTION)) {}
  }
}

object ThreeDAnnotation extends ModelCompanion[ThreeDAnnotation, ObjectId] {
  val COLLECTION = "previews.files.annotations"

  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ThreeDAnnotation, ObjectId](collection = x.collection(COLLECTION)) {}
  }
}
