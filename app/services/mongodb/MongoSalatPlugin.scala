package services.mongodb

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import play.api.{ Plugin, Logger, Application }
import play.api.Play.current
import com.mongodb.casbah.MongoURI
import com.mongodb.casbah.MongoConnection
import com.mongodb.casbah.MongoDB
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.gridfs.GridFS
import services.{DI, AppConfigurationService}

/**
 * Mongo Salat service.
 *
 * @author Rob Kooper
 *
 */
class MongoSalatPlugin(app: Application) extends Plugin {
  // URI to the mongodatabase, for example mongodb://127.0.0.1:27017/medici
  var mongoURI: MongoURI = null

  // hold the connection, if connection failed it will be tried to open next time
  var mongoConnection: MongoConnection = null

  override def onStart() {
    mongoURI = if (play.api.Play.configuration.getString("mongodbURI").isDefined) {
      MongoURI(play.api.Play.configuration.getString("mongodbURI").get)
    } else if (play.api.Play.configuration.getString("mongodb.default").isDefined) {
      Logger.info("mongodb.default is deprecated, please use mongodbURI")
      MongoURI(play.api.Play.configuration.getString("mongodb.default").get)
    } else {
      Logger.info("no connection to mongo specified in , will use default URI mongodb://127.0.0.1:27017/medici")
      MongoURI("mongodb://127.0.0.1:27017/medici")
    }

    // connect to the database
    mongoConnection = mongoURI.connect.fold(l => throw l, r => r)

    // update database if needed
    updateDatabase()

    // create indices.
    Logger.debug("Ensuring indices exist")
    collection("collections").ensureIndex(MongoDBObject("created" -> -1))
    
    collection("datasets").ensureIndex(MongoDBObject("created" -> -1))
    collection("datasets").ensureIndex(MongoDBObject("tags" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("files._id" -> 1))
    
    collection("uploads.files").ensureIndex(MongoDBObject("uploadDate" -> -1))
    collection("uploads.files").ensureIndex(MongoDBObject("tags" -> 1))
    collection("uploads.files").ensureIndex(MongoDBObject("author.email" -> 1))

    collection("uploadquery.files").ensureIndex(MongoDBObject("uploadDate" -> -1))
    
    collection("previews.files").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
    collection("previews.files").ensureIndex(MongoDBObject("uploadDate" -> -1, "section_id" -> 1))
    collection("previews.files").ensureIndex(MongoDBObject("section_id" -> -1))
    collection("previews.files").ensureIndex(MongoDBObject("file_id" -> -1))
    
    collection("textures.files").ensureIndex(MongoDBObject("file_id" -> 1))
    collection("tiles.files").ensureIndex(MongoDBObject("preview_id" -> 1, "filename" -> 1,"level" -> 1))
    
    collection("sections").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
    collection("sections").ensureIndex(MongoDBObject("file_id" -> -1))
    
    collection("dtsrequests").ensureIndex(MongoDBObject("startTime" -> -1, "endTime" -> -1))
    collection("dtsrequests").ensureIndex(MongoDBObject("file_id" -> -1))
    collection("versus.descriptors").ensureIndex(MongoDBObject("fileId" -> 1))

  }

  override def onStop() {
    if (mongoConnection != null)
      mongoConnection.close()
    mongoConnection = null
  }

  /**
   * Returns the database for the connection
   */
  def getDB: MongoDB = mongoConnection.getDB(mongoURI.database.getOrElse("medici"))

  /**
   * Returns a collection in the database
   */
  def collection(collection: String): MongoCollection = getDB(collection)
  
  /**
   * Returns a GridFS for writing files, the files will be placed in
   * two collections that start with the prefix (&lt;prefix&gt;.fs and
   * &lt;prefix.chunks&gt;).
   */
  def gridFS(prefix: String = "fs"): GridFS = GridFS(getDB, prefix)

  /**
   * Drop all collections
   */
  def dropAllData() {
    Logger.debug("**DANGER** Deleting data collections **DANGER**")
    collection("collections").drop()
    collection("comments").drop()
    collection("datasets").drop()
    collection("dtsrequests").drop()
    collection("extractions").drop()
    collection("extractor.servers").drop()
    collection("extractor.names").drop()
    collection("extractor.inputtypes").drop()
    collection("multimedia.features").drop()
    collection("previews.chunks").drop()
    collection("previews.files").drop()
    collection("sections").drop()
    collection("streams").drop()
    collection("thumbnails.chunks").drop()
    collection("thumbnails.files").drop()
    collection("uploads.chunks").drop()
    collection("uploads.files").drop()
    collection("uploadquery.files").drop()
    collection("versus.descriptors").drop()
    Logger.debug("**DANGER** Data deleted **DANGER**")
  }

  // ----------------------------------------------------------------------
  // CODE TO UPDATE THE DATABASE
  // ----------------------------------------------------------------------
  def updateDatabase() {
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

    // migrate users to new model
    if (!appConfig.hasPropertyValue("mongodb.updates", "fixing-typehint-users")) {
      if (System.getProperty("MONGOUPDATE") != null) {
        Logger.info("[MongoDBUpdate] : Fixing _typeHint for users.")
        val q = MongoDBObject("_typeHint" -> "securesocial.core.SocialUser")
        val o = MongoDBObject("$set" -> MongoDBObject("_typeHint" -> "models.ClowderUser"))
        collection("social.users").update(q, o, multi=true, concern=WriteConcern.Safe)
        appConfig.addPropertyValue("mongodb.updates", "fixing-typehint-users")
      } else {
        Logger.warn("[MongoDBUpdate] : Missing fix _typeHint for users.")
      }
    }
  }
}
