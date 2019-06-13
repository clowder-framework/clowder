package services.mongodb

import services._
import play.api.Logger
import com.mongodb.casbah.Imports._
import play.api.Play.current
import javax.inject.{Inject, Singleton}
import models.{DBCounts, ResourceRef}

/**
  * App Configuration Service.
 */
class MongoDBAppConfigurationService extends AppConfigurationService {
  def addPropertyValue(key: String, value: Any) {
    getCollection.update(MongoDBObject("key" -> key), $addToSet("value" -> value), upsert=true, concern=WriteConcern.Safe)
  }

  def removePropertyValue(key: String, value: Any) {
    getCollection.update(MongoDBObject("key" -> key), $pull("value" -> value), concern=WriteConcern.Safe)
  }

  def hasPropertyValue(key: String, value: Any) = {
    getCollection.findOne(("value" $in value :: Nil) ++ ("key" -> key)).nonEmpty
  }

  /**
   * Gets the configuration property with the specified key. If the key is not found
   * it wil return None.
   */
  def getProperty[objectType <: Any](key: String): Option[objectType] = {
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
  def setProperty(key: String, value: Any): Option[Any] = {
    Logger.debug(s"Setting $key to $value")
    val old = getProperty(key)
    getCollection.update(MongoDBObject("key" -> key), $set("value" -> value), upsert=true, concern=WriteConcern.Safe)
    old
  }

  /**
   * Remove the configuration property with the specified key and returns the value if any
   * was set, otherwise it will return None.
   */
  def removeProperty(key: String): Option[Any] = {
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

  /** Try to get counts from appConfig, returning 0 if not found **/
  def getIndexCounts(): DBCounts = {
    Logger.debug("Loading instance counts from appConfig")
    new DBCounts(
      getProperty[Long]("countof.datasets").getOrElse(0),
      getProperty[Long]("countof.files").getOrElse(0),
      getProperty[Long]("countof.bytes").getOrElse(0),
      getProperty[Long]("countof.collections").getOrElse(0),
      getProperty[Long]("countof.spaces").getOrElse(0),
      getProperty[Long]("countof.users").getOrElse(0)
    )
  }

  /** Increment configuration property with specified key by value. **/
  def incrementCount(key: Symbol, value: Long) = {
    val fullKey = key match {
      case 'users => "countof.users"
      case 'files => "countof.files"
      case 'bytes => "countof.bytes"
      case 'datasets => "countof.datasets"
      case 'collections => "countof.collections"
      case 'spaces => "countof.spaces"
      case _ => ""
    }
    getCollection.update(MongoDBObject("key" -> fullKey), $inc("value" -> value),
      upsert=true, concern=WriteConcern.Safe)
  }

  /** Reset configuration property with specified key to zero. **/
  def resetCount(key: Symbol) = {
    val fullKey = key match {
      case 'users => "countof.users"
      case 'files => "countof.files"
      case 'bytes => "countof.bytes"
      case 'datasets => "countof.datasets"
      case 'collections => "countof.collections"
      case 'spaces => "countof.spaces"
      case _ => ""
    }
    val zero: Long = 0L
    getCollection.update(MongoDBObject("key" -> fullKey), $inc("value" -> zero),
      upsert=true, concern=WriteConcern.Safe)
  }

}
