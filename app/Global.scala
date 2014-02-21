import com.mongodb.casbah.Imports._
import models.{UUID, Foo}
import org.bson.types.ObjectId
import play.api.{GlobalSettings, Application}
import play.api.Logger
import play.api.Play.current
import services.mongodb.MongoSalatPlugin

/**
 * Configure application. Ensure mongo indexes if mongo plugin is enabled.
 *
 * @author Luigi Marini
 */
object Global extends GlobalSettings {

  override def onStart(app: Application) {
    // create mongo indexes if plugin is loaded
    current.plugin[MongoSalatPlugin].map {
      mongo =>
        mongo.sources.values.map {
          source =>
            Logger.debug("Ensuring indexes on " + source.uri)
            source.collection("datasets").ensureIndex(MongoDBObject("created" -> -1))
            source.collection("datasets").ensureIndex(MongoDBObject("tags" -> 1))
            source.collection("uploads.files").ensureIndex(MongoDBObject("uploadDate" -> -1))
            source.collection("uploadquery.files").ensureIndex(MongoDBObject("uploadDate" -> -1))
            source.collection("previews.files").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
            source.collection("previews.files").ensureIndex(MongoDBObject("uploadDate" -> -1, "section_id" -> 1))
            source.collection("sections").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
        }
    }
  }

  override def onStop(app: Application) {
  }

  private lazy val injector = services.DI.injector

  /** Used for dynamic controller dispatcher **/
  override def getControllerInstance[A](clazz: Class[A]) = {
    injector.getInstance(clazz)
  }
}
