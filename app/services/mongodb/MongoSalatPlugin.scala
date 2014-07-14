package services.mongodb

import play.api.{ Plugin, Logger, Application, Configuration }
import com.mongodb.casbah.MongoURI
import com.mongodb.casbah.MongoConnection
import play.api.Mode
import com.mongodb.MongoException
import com.mongodb.casbah.MongoDB
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.gridfs.GridFS

/**
 * Mongo Salat service.
 *
 * @author Rob Kooper
 *
 */
class MongoSalatPlugin(app: Application) extends Plugin {

  case class MongoSource(uri: MongoURI) {
    var conn : MongoConnection = null;
    lazy val db = open.getDB(uri.database.getOrElse("medici"))
    
    def collection(name: String): MongoCollection = db(name)
    
    def open = {
      if (conn == null) {
        conn = uri.connect.fold(l => throw(l), r => r)
      }
      conn
    }

     def close = {
       if (conn != null) {
         conn.close
         conn = null
       }  
     }
  }

  object MongoSource {
    lazy val conn = MongoConnection()
  }

  lazy val configuration = app.configuration.getConfig("mongodb").getOrElse(Configuration.empty)
  lazy val sources: Map[String, MongoSource] = configuration.subKeys.map { sourceKey =>
    val uri = configuration.getString(sourceKey)
    val source = uri match {
      case None => null
      case Some(x) => MongoSource(MongoURI(x))
    }
    sourceKey -> source
  }.toMap

  override def onStart() {
    if (sources.isEmpty) {
      Logger.error("no connections specificed in conf file.")
    }
    app.mode match {
      case Mode.Test =>
      case _ => {
        sources.map { source =>
          try {
            source._2.db.collectionNames
          } catch {
            case e: MongoException => throw configuration.reportError("mongodb." + source._1, "couldn't connect to [" + source._2.uri + "]", Some(e))
          } finally {
            Logger.info("[mongoplugin]  connected '" + source._1 + "' to " + source._2.uri)
          }
        }
      }
    }
  }

  override def onStop() {
    sources.map { source =>
	  try {
      // Only close connection if not in test mode
      if (app.mode != Mode.Test) source._2.close
	  } catch {
	    case e: MongoException => throw configuration.reportError("mongodb." + source._1, "couldn't close [" + source._2.uri + "]", Some(e))
	  } finally {
	    Logger.info("[mongoplugin] closed '" + source._1 + "' to " + source._2.uri)
	  }
    }
  }

  override def enabled = !configuration.subKeys.isEmpty
  
  /**
   * Returns the MongoSource that has been configured in application.conf
   * @param source The source name ex. default
   * @return A MongoSource
   */
  def source(source: String): MongoSource = {
    sources.get(source).getOrElse(throw configuration.reportError("mongodb." + source, source + " doesn't exist"))
  }
 
  /**
   * Returns MongoDB for configured source
   * @param sourceName The source name ex. default
   * @return A MongoDB
   */
  def db(sourceName:String = "default"): MongoDB = source(sourceName).db

  /**
   * Returns MongoCollection that has been configured in application.conf
   * @param collectionName The MongoDB collection name
   * @param sourceName The source name ex. default
   * @return A MongoCollection
   */
  def collection(collectionName: String, sourceName: String = "default"): MongoCollection = source(sourceName).collection(collectionName)
  
  
  /**
   * Returns GridFS for configured source
   * @param bucketName The bucketName for the GridFS instance
   * @param sourceName The source name ex. default
   * @return A GridFS
   */
  def gridFS(bucketName: String = "fs", sourceName:String = "default"): GridFS = GridFS(source(sourceName).db, bucketName)

  def dropAllData() {
    sources.values.map { source =>
      Logger.debug("**DANGER** Deleting data collections **DANGER**")
      source.collection("collections").drop()
      source.collection("datasets").drop()
      source.collection("previews.chunks").drop()
      source.collection("previews.files").drop()
      source.collection("sections").drop()
      source.collection("uploads.chunks").drop()
      source.collection("uploads.files").drop()
      source.collection("uploadquery.files").drop()
      source.collection("extractions").drop()
      source.collection("extractor.servers").drop()
      source.collection("extractor.names").drop()
      source.collection("extractor.inputtypes").drop()
      source.collection("dtsrequests").drop()
      source.collection("streams").drop()
      Logger.debug("**DANGER** Data deleted **DANGER**")
    }
  }

}