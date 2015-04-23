package services.mongodb

import services.AppConfigurationService
import play.api.Logger
import com.mongodb.casbah.Imports._
import play.api.Play.current

/**
 * @author Luigi Marini
 * @author Rob Kooper
 */
class MongoDBAppConfigurationService extends AppConfigurationService {
  def addPropertyValue(key: String, value: AnyRef) {
    getCollection.update(MongoDBObject("key" -> key), $addToSet("value" -> value), upsert=true, concern=WriteConcern.Safe)
  }

  def removePropertyValue(key: String, value: AnyRef) {
    getCollection.update(MongoDBObject("key" -> key), $pull("value" -> value), concern=WriteConcern.Safe)
  }

  def hasPropertyValue(key: String, value: AnyRef) = {
    getCollection.findOne(("value" $in value :: Nil) ++ ("key" -> key)).nonEmpty
  }

  /**
   * Gets the configuration property with the specified key. If the key is not found
   * it wil return None.
   */
  def getProperty[objectType <: AnyRef](key: String): Option[objectType] = {
    Logger.debug(s"Getting value for $key")
    getCollection.findOne(MongoDBObject("key" -> key)) match {
      case Some(x) => {
        x.get("value") match {
          case l:BasicDBList => Some(l.toList.asInstanceOf[objectType])
          case y => Some(y.asInstanceOf[objectType])
        }
      }
      case None => None
    }
  }

  /**
   * Sets the configuration property with the specified key to the specified value. If the
   * key already existed it will return the old value, otherwise it returns None.
   */
  def setProperty(key: String, value: AnyRef): Option[AnyRef] = {
    Logger.debug(s"Setting $key to $value")
    val old = getProperty(key)
    getCollection.update(MongoDBObject("key" -> key), $set("value" -> value), upsert=true, concern=WriteConcern.Safe)
    old
  }

  /**
   * Remove the configuration property with the specified key and returns the value if any
   * was set, otherwise it will return None.
   */
  def removeProperty(key: String): Option[AnyRef] = {
    Logger.debug(s"Removing value for $key")
    val collection = getCollection
    collection.findOne(MongoDBObject("key" -> key)) match {
      case Some(x) => {
        collection.remove(MongoDBObject("key" -> key))
        Some(x.get("value"))
      }
      case None => {
        None
      }
    }
  }

  /** returns the collection with app configuration values */
  def getCollection = {
    current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("Mongo not configured");
      case Some(mongo) => mongo.collection("app.configuration")
    }
  }
}
