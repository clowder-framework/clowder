package services.mongodb

import java.io.InputStream

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import models.{User, Logo, UUID}
import play.api.Play.current
import services.{ByteStorageService, LogoService}
import services.mongodb.MongoContext.context
import util.FileUtils

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
    ByteStorageService.save(inputStream, LogoDAO.COLLECTION) match {
      case Some(x) => {
        val logo = Logo(UUID.generate(), x._1, x._3, x._4, x._2, path, name, FileUtils.getContentType(name, contentType), author)
        LogoDAO.save(logo)
        Some(logo)
      }
      case None => None
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
    LogoDAO.findOne(MongoDBObject("path" -> path, "name" -> name)).flatMap { logo =>
      ByteStorageService.load(logo.loader, logo.loader_id, LogoDAO.COLLECTION).map((_, "", logo.contentType, logo.length))
    }
  }

  /**
   * Get the input stream of a file given a file id.
   * Returns input stream, file name, content type, content length.
   */
  override def getBytes(id: UUID): Option[(InputStream, String, String, Long)] = {
    LogoDAO.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))).flatMap { logo =>
      ByteStorageService.load(logo.loader, logo.loader_id, LogoDAO.COLLECTION).map((_, "", logo.contentType, logo.length))
    }
  }

  /**
   * Remove the file from mongo
   */
  override def delete(path: String, name: String): Unit = {
    LogoDAO.findOne(MongoDBObject("path" -> path, "name" -> name)).foreach { logo =>
      ByteStorageService.delete(logo.loader, logo.loader_id, LogoDAO.COLLECTION)
      LogoDAO.remove(logo)
    }
  }

  /**
   * Remove the file from mongo
   */
  override def delete(id: UUID): Unit = {
    LogoDAO.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))).foreach { logo =>
      ByteStorageService.delete(logo.loader, logo.loader_id, LogoDAO.COLLECTION)
      LogoDAO.remove(logo)
    }
  }

  object LogoDAO extends ModelCompanion[Logo, ObjectId] {
    val COLLECTION = "logos"

    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[Logo, ObjectId](collection = x.collection(COLLECTION)) {}
    }
  }

}
