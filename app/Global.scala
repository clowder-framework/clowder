import com.mongodb.casbah.Imports._
import play.api.{ GlobalSettings, Application }
import play.api.Logger
import play.api.Play.current
import services._
import play.libs.Akka
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import models.DTSInfoSetUp
import services.ExtractorService
import java.util.Date
import java.util.Calendar
import models._
/**
 * Configure application. Ensure mongo indexes if mongo plugin is enabled.
 *
 * @author Luigi Marini
 */
object Global extends GlobalSettings {
      var serverStartTime:Date=null
  override def onStart(app: Application) {
    // create mongo indexes if plugin is loaded
    
    models.ServerStartTime.startTime = Calendar.getInstance().getTime()
    serverStartTime = models.ServerStartTime.startTime
    Logger.debug("\n----Server Start Time----" + serverStartTime + "\n \n")
    
    current.plugin[MongoSalatPlugin].map { mongo =>
      mongo.sources.values.map { source =>
        Logger.debug("Ensuring indexes on " + source.uri)
        source.collection("datasets").ensureIndex(MongoDBObject("created" -> -1))
        source.collection("datasets").ensureIndex(MongoDBObject("tags" -> 1))
        source.collection("uploads.files").ensureIndex(MongoDBObject("uploadDate" -> -1))
        source.collection("uploadquery.files").ensureIndex(MongoDBObject("uploadDate" -> -1))
        source.collection("previews.files").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
        source.collection("previews.files").ensureIndex(MongoDBObject("uploadDate" -> -1, "section_id" -> 1))
        source.collection("sections").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))

        source.collection("extractor.servers").ensureIndex(MongoDBObject("server" -> ""))
        source.collection("extractor.names").ensureIndex(MongoDBObject("name" -> ""))
        source.collection("extractor.inputtypes").ensureIndex(MongoDBObject("inputType" -> ""))
        source.collection("dtsrequests").ensureIndex(MongoDBObject("startTime" -> -1, "endTime" -> -1))
      }
    }
    
    //Delete garbage files (ie past intermediate extractor results files) from DB
    var timeInterval = play.Play.application().configuration().getInt("intermediateCleanup.checkEvery")
    Akka.system().scheduler.schedule(0.hours, timeInterval.intValue().hours){
      models.FileDAO.removeOldIntermediates()
    }
  //Clean temporary RDF files if RDF exporter is activated
    if(play.Play.application().configuration().getString("rdfexporter").equals("on")){
	    timeInterval = play.Play.application().configuration().getInt("rdfTempCleanup.checkEvery")
	    Akka.system().scheduler.schedule(0.minutes, timeInterval.intValue().minutes){
	      models.FileDAO.removeTemporaries()
	    }
    }
    
    
    
    Akka.system().scheduler.schedule(0.minutes,5 minutes){
           models.DTSInfoSetUp.updateExtractorsInfo()
           models.DTSInfoSetUp.updateDTSRequests()
           
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
