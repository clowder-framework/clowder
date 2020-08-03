package services.mongodb

import com.mongodb.casbah.MongoCollection

trait MongoStartup {
  def collection(collection: String): MongoCollection
}
