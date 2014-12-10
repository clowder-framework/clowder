import play.api.{GlobalSettings, Application}
import play.api.Logger
import play.api.Play.current

import play.api.mvc.WithFilters
import play.filters.gzip.GzipFilter 

import play.libs.Akka
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import models.ExtractionInfoSetUp
import java.util.Date
import java.util.Calendar
import models._
import akka.actor.Cancellable

/**
 * Configure application. Ensure mongo indexes if mongo plugin is enabled.
 *
 * @author Luigi Marini
 */
object Global extends WithFilters(new GzipFilter(),CORSFilter()) with GlobalSettings {
        
  var serverStartTime:Date=null
  
  var extractorTimer: Cancellable = null
  
  override def onStart(app: Application) {
    ServerStartTime.startTime = Calendar.getInstance().getTime
    serverStartTime = ServerStartTime.startTime
    Logger.debug("\n----Server Start Time----" + serverStartTime + "\n \n")
    
    //Add permanent admins to app if not already included
    // TODO this should be a service so we can store it in other places than mongo
    val appConfObj = new services.mongodb.MongoDBAppConfigurationService{}    
    appConfObj.getDefault()
    for(initialAdmin <- play.Play.application().configuration().getString("initialAdmins").split(","))
    	appConfObj.addAdmin(initialAdmin)

    extractorTimer = Akka.system().scheduler.schedule(0.minutes,5 minutes){
           models.ExtractionInfoSetUp.updateExtractorsInfo()
    }
    Logger.info("Application has started")
  }

  override def onStop(app: Application) {
    extractorTimer.cancel()
    Logger.info("Application shutdown")
  }

  private lazy val injector = services.DI.injector

  /** Used for dynamic controller dispatcher **/
  override def getControllerInstance[A](clazz: Class[A]) = {
    injector.getInstance(clazz)
  }
}
