package services.mongodb

import java.io.InputStream

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import models.{User, Logo, UUID}
import play.api.Play.current
import services.LogoService
import services.mongodb.MongoContext.context

class MongoDBLogoService extends LogoService {
  /**
   * Return list of all logos
   */
  override def list(path: Option[String], name: Option[String]): List[Logo] = {
    val query = (path, name) match {
      case (Some(p), Some(n)) => MongoDBObject("path" -> p, "name" -> n)
      case (Some(p), None) => MongoDBObject("path" -> p)
      case (None, Some(n)) => MongoDBObject("name" -> n)
      case (_, _) => MongoDBObject()
    }
    LogoDAO.find(query).toList
  }

  /**
   * Save a file from an input stream.
   */
  override def save(inputStream: InputStream, path: String, name: String, showText: Boolean, contentType: Option[String], author: User): Option[Logo] = {
    MongoUtils.writeBlob[Logo](inputStream, name, contentType, Map.empty[String, Any], LogoDAO.COLLECTION, LogoDAO.FLAG).map { result =>
      val logo = Logo(UUID.generate(), result._1, result._3, result._4, result._2, path, name, contentType.getOrElse(play.api.http.ContentTypes.BINARY), author)
      LogoDAO.save(logo)
      logo
    }
  }

  override def update(logo: Logo): Unit = {
    LogoDAO.save(logo)
  }

  /**
   * Return the specified object.
   */
  override def get(path: String, name: String): Option[Logo] = {
    LogoDAO.findOne(MongoDBObject("path" -> path, "name" -> name))
  }

  /**
   * Return the specified object.
   */
  override def get(id: UUID): Option[Logo] = {
    LogoDAO.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify)))
  }

  /**
   * Get the input stream of a file given a file id.
   * Returns input stream, file name, content type, content length.
   */
  override def getBytes(path: String, name: String): Option[(InputStream, String, String, Long)] = {
    LogoDAO.findOne(MongoDBObject("path" -> path, "name" -> name)).flatMap{logo =>
      MongoUtils.readBlob(logo.file_id, LogoDAO.COLLECTION, LogoDAO.FLAG)
    }
  }

  /**
   * Get the input stream of a file given a file id.
   * Returns input stream, file name, content type, content length.
   */
  override def getBytes(id: UUID): Option[(InputStream, String, String, Long)] = {
    LogoDAO.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))).flatMap { logo =>
      MongoUtils.readBlob(logo.file_id, LogoDAO.COLLECTION, LogoDAO.FLAG)
    }
  }

  /**
   * Remove the file from mongo
   */
  override def delete(path: String, name: String): Unit = {
    LogoDAO.findOne(MongoDBObject("use" -> path, "filename" -> name)).foreach { logo =>
      MongoUtils.removeBlob(logo.file_id, LogoDAO.COLLECTION, LogoDAO.FLAG)
    }
  }

  /**
   * Remove the file from mongo
   */
  override def delete(id: UUID): Unit = {
    LogoDAO.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))).foreach { logo =>
      MongoUtils.removeBlob(logo.file_id, LogoDAO.COLLECTION, LogoDAO.FLAG)
    }
  }

  object LogoDAO extends ModelCompanion[Logo, ObjectId] {
    val COLLECTION = "logos"
    val FLAG = "medici2.mongodb.store.logos"
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[Logo, ObjectId](collection = x.collection(COLLECTION)) {}
    }
  }

}
