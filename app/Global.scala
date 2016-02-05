import java.io.{StringWriter, PrintWriter}
import play.api.{GlobalSettings, Application}
import play.api.Logger
import play.filters.gzip.GzipFilter
import play.libs.Akka
import services.{UserService, DI, AppConfiguration}
import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import models._
import java.util.Calendar
import play.api.mvc.{RequestHeader, WithFilters}
import play.api.mvc.Results._
import akka.actor.Cancellable
import julienrf.play.jsonp.Jsonp

/**
 * Configure application. Ensure mongo indexes if mongo plugin is enabled.
 *
 * @author Luigi Marini
 */
object Global extends WithFilters(new GzipFilter(), new Jsonp(), CORSFilter()) with GlobalSettings {
  var extractorTimer: Cancellable = null
  var jobTimer: Cancellable = null


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

    // set default metadata definitions
    MetadataDefinition.registerDefaultDefinitions()

    if (extractorTimer == null) {
      extractorTimer = Akka.system().scheduler.schedule(0 minutes, 5 minutes) {
        ExtractionInfoSetUp.updateExtractorsInfo()
      }
    }

    // Use if Mailer Server and stmp in Application.conf are set up
    if (jobTimer == null) {
      jobTimer = Akka.system().scheduler.schedule(0 minutes, 1 minutes) {
        JobsScheduler.runScheduledJobs()
      }
    }

    Logger.info("Application has started")
  }

  override def onStop(app: Application) {
    extractorTimer.cancel()
    jobTimer.cancel()
    Logger.info("Application shutdown")
  }

  private lazy val injector = services.DI.injector

  /** Used for dynamic controller dispatcher **/
  override def getControllerInstance[A](clazz: Class[A]) = {
    injector.getInstance(clazz)
  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    ex.printStackTrace(pw)
    Future(InternalServerError(
      views.html.errorPage(request, sw.toString.replace("\n", "   "))
    ))
  }

  override def onHandlerNotFound(request: RequestHeader) = {

    Future(NotFound(
      views.html.errorPage(request, "Not found")
    ))
  }

  override def onBadRequest(request: RequestHeader, error: String) = {
    Future(BadRequest(views.html.errorPage(request, error)))
  }
}
