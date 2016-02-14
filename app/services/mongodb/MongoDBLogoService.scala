package services.mongodb

import java.io.InputStream

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import models.{Logo, UUID}
import play.api.Play.current
import securesocial.core.Identity
import services.LogoService
import services.mongodb.MongoContext.context

class MongoDBLogoService extends LogoService {
  /**
   * Return list of all logos
   */
  override def list(path: Option[String], name: Option[String]): List[Logo] = {
    val query = (path, name) match {
      case (Some(p), Some(n)) => MongoDBObject("use" -> p, "filename" -> n)
      case (Some(p), None) => MongoDBObject("use" -> p)
      case (_, _) => MongoDBObject()
    }
    LogoDAO.find(query).toList
  }

  /**
   * Save a file from an input stream.
   */
  override def save(inputStream: InputStream, path: String, name: String, showText: Boolean, contentType: Option[String], author: Identity): Option[Logo] = {
    val extra = Map("use" -> path, "author" -> SocialUserDAO.toDBObject(author), "showText" -> showText)
    MongoUtils.writeBlob[Logo](inputStream, name, contentType, extra, LogoDAO.COLLECTION, LogoDAO.FLAG).flatMap(x => get(x._1))
  }

  override def update(logo: Logo): Unit = {
    LogoDAO.save(logo)
  }

  /**
   * Return the specified object.
   */
  override def get(path: String, name: String): Option[Logo] = {
    LogoDAO.findOne(MongoDBObject("use" -> path, "filename" -> name))
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
    LogoDAO.findOne(MongoDBObject("use" -> path, "filename" -> name)).flatMap{logo =>
      MongoUtils.readBlob(logo.id, LogoDAO.COLLECTION, LogoDAO.FLAG)
    }
  }

  /**
   * Get the input stream of a file given a file id.
   * Returns input stream, file name, content type, content length.
   */
  override def getBytes(id: UUID): Option[(InputStream, String, String, Long)] = {
    MongoUtils.readBlob(id, LogoDAO.COLLECTION, LogoDAO.FLAG)
  }

  /**
   * Remove the file from mongo
   */
  override def delete(path: String, name: String): Unit = {
    LogoDAO.findOne(MongoDBObject("use" -> path, "filename" -> name)).foreach { logo =>
      MongoUtils.removeBlob(logo.id, LogoDAO.COLLECTION, LogoDAO.FLAG)
    }
  }

  /**
   * Remove the file from mongo
   */
  override def delete(id: UUID): Unit = {
    MongoUtils.removeBlob(id, LogoDAO.COLLECTION, LogoDAO.FLAG)
  }

  object LogoDAO extends ModelCompanion[Logo, ObjectId] {
    val COLLECTION = "logos"
    val FLAG = "medici2.mongodb.storeFiles"
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[Logo, ObjectId](collection = x.collection(COLLECTION + ".files")) {}
    }
  }

}
