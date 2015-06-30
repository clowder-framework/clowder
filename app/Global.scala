import api.Permission
import play.api.{GlobalSettings, Application}
import play.api.Logger

import play.filters.gzip.GzipFilter

import play.libs.Akka
import services.{UserService, DI, AppConfiguration}
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import models.{Role, ServerStartTime, CORSFilter, ExtractionInfoSetUp}
import java.util.Calendar
import play.api.mvc.WithFilters
import akka.actor.Cancellable
import julienrf.play.jsonp.Jsonp

/**
 * Configure application. Ensure mongo indexes if mongo plugin is enabled.
 *
 * @author Luigi Marini
 */
object Global extends WithFilters(new GzipFilter(), new Jsonp(), CORSFilter()) with GlobalSettings {
  var extractorTimer: Cancellable = null

  override def onStart(app: Application) {
    ServerStartTime.startTime = Calendar.getInstance().getTime
    Logger.debug("\n----Server Start Time----" + ServerStartTime.startTime + "\n \n")

    // set admins
    AppConfiguration.setDefaultAdmins()

    // create default roles
    val users: UserService = DI.injector.getInstance(classOf[UserService])
    if (users.listRoles().isEmpty) {
      Logger.debug("Ensuring roles exist")
      users.updateRole(Role.Admin)
      users.updateRole(Role.Editor)
      users.updateRole(Role.Viewer)
    }


    extractorTimer = Akka.system().scheduler.schedule(0 minutes, 5 minutes) {
      ExtractionInfoSetUp.updateExtractorsInfo()
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
