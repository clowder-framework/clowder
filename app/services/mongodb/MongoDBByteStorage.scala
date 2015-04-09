package services.mongodb

import java.io.InputStream

import models.UUID
import services.ByteStorageService

/**
 * Store the bytes in a mongo gridFS.
 *
 * @author Rob Kooper
 */
class MongoDBByteStorage extends ByteStorageService {
  /**
   * Save the bytes to mongo, the prefix is used for the collection and id is
   * ignored.
   */
  def save(inputStream: InputStream, collection: String, id: UUID): Option[String] = {
    MongoUtils.writeBlob(inputStream, "unknown", None, Map.empty[String, AnyRef], collection, "nevereverset").map(_.stringify)
  }

  /**
   * Get the bytes from Mongo
   */
  def load(id: String, collection: String): Option[InputStream] = {
    MongoUtils.readBlob(UUID(id), collection, "nevereverset") match {
      case Some((is, _, _, _)) => Some(is)
      case None => None
    }
  }

  /**
   * Delete actualy bytes from Mongo
   */
  def delete(id: String, collection: String): Boolean = {
    MongoUtils.removeBlob(UUID(id), collection, "nevereverset")
  }
}
