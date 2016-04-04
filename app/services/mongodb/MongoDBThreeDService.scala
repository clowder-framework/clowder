package services.mongodb

import services.ThreeDService
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

/**
 * Use MongoDB to store 3-d's
 */
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
    MongoUtils.writeBlob[ThreeDTexture](inputStream, filename, contentType, Map.empty[String, AnyRef], "textures", "nevereverused").fold("")(_._1.stringify)
  }

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    MongoUtils.readBlob(id, "textures", "nevereverused")
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
    MongoUtils.writeBlob[Geometry](inputStream, filename, contentType, Map.empty[String, AnyRef], "geometries", "nevereverused").fold("")(_._1.stringify)
  }

  def getGeometry(id: UUID): Option[ThreeDGeometry] = {
    GeometryDAO.findOneById(new ObjectId(id.stringify))
  }

  /**
   * Get blob.
   */
  def getGeometryBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    MongoUtils.readBlob(id, "geometries", "nevereverused")
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
