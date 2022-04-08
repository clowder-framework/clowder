import java.io.{PrintWriter, StringWriter}
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import play.api.{Application, GlobalSettings}
import play.api.Logger
import play.filters.gzip.GzipFilter
import play.libs.Akka
import securesocial.core.SecureSocial
import services.{AppConfiguration, AppConfigurationService, CollectionService, DI, DatasetService, FileService, SpaceService, UserService}

import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import models._
import java.util.{Calendar, Date}

import play.api.mvc.{RequestHeader, WithFilters}
import play.api.mvc.Results._
import akka.actor.Cancellable
import filters.CORSFilter
import julienrf.play.jsonp.Jsonp
import play.Play
import play.api.Play.configuration
import play.api.libs.json.Json._

/**
 * Configure application. Ensure mongo indexes if mongo plugin is enabled.
 *
 * @author Luigi Marini
 */
object Global extends WithFilters(new GzipFilter(), new Jsonp(), CORSFilter()) with GlobalSettings {
  var extractorTimer: Cancellable = null
  var jobTimer: Cancellable = null
  var archivalTimer: Cancellable = null


  override def onStart(app: Application) {
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])
    val files: FileService = DI.injector.getInstance(classOf[FileService])

    ServerStartTime.startTime = Calendar.getInstance().getTime
    Logger.debug("\n----Server Start Time----" + ServerStartTime.startTime + "\n \n")

    val users: UserService = DI.injector.getInstance(classOf[UserService])

    // get clowder unique ID
    Logger.info(s"Starting clowder with id = ${AppConfiguration.getInstance}")

    // set the default ToS version
    AppConfiguration.setDefaultTermsOfServicesVersion()

    // add all new admins
    users.updateAdmins()

    // create default roles
    if (users.listRoles().isEmpty) {
      Logger.debug("Ensuring roles exist")
      users.updateRole(Role.Admin)
      users.updateRole(Role.Editor)
      users.updateRole(Role.Viewer)
    }

    // set default metadata definitions
    MetadataDefinition.registerDefaultDefinitions()

    val archiveEnabled = Play.application.configuration.getBoolean("archiveEnabled", false)
    if (archiveEnabled && archivalTimer == null) {
      // Set archiveAutoInterval == 0 to disable auto archiving
      val archiveAutoInterval = Play.application.configuration.getLong("archiveAutoInterval", 0)
      if (archiveAutoInterval > 0) {
        val interval = FiniteDuration(archiveAutoInterval, SECONDS)
        val archiveAutoDelay = Play.application.configuration.getLong("archiveAutoDelay", 0)
        val delay = FiniteDuration(archiveAutoDelay, SECONDS)

        Logger.info("Starting archival loop - first iteration in " + delay + ", next iteration after " + interval)
        archivalTimer = Akka.system.scheduler.schedule(delay, interval) {
          Logger.info("Starting auto archive process...")
          files.autoArchiveCandidateFiles()
        }
      }
    }

    if (extractorTimer == null) {
      extractorTimer = Akka.system().scheduler.schedule(0 minutes, 5 minutes) {
        ExtractionInfoSetUp.updateExtractorsInfo()
      }
    }

    if (jobTimer == null) {
      jobTimer = Akka.system().scheduler.schedule(0 minutes, 1 minutes) {
        JobsScheduler.runScheduledJobs()
      }
    }

    // Get database counts from appConfig; generate them if unavailable or user count = 0
    appConfig.getProperty[Long]("countof.bytes") match {
      case Some(filesBytes) =>
        Logger.info("Byte count found in appConfig; skipping database counting")
      case None => {
        // Reset byte count to zero before incrementing
        appConfig.resetCount('bytes)

        Logger.info("Byte count not found in appConfig; scheduling database counting in 10s...")
        Akka.system().scheduler.scheduleOnce(10 seconds) {
          Logger.debug("Initializing appConfig byte count...")
          val files: FileService = DI.injector.getInstance(classOf[FileService])

          // Store the byte count in appConfig so it can be fetched quickly later
          appConfig.incrementCount('bytes, files.bytes())
          Logger.info("Initialized appConfig byte count")
        }
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
  private lazy val users: UserService =  DI.injector.getInstance(classOf[UserService])

  /** Used for dynamic controller dispatcher **/
  override def getControllerInstance[A](clazz: Class[A]) = {
    injector.getInstance(clazz)
  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    ex.printStackTrace(pw)

    if (request.path.contains("/api/")) {
      Future(InternalServerError(toJson(Map("status" -> "error",
        "request" -> request.toString(),
        "exception" -> sw.toString.replace("\n", "\\n")))))
    } else {
      implicit val user = SecureSocial.currentUser(request) match{
        case Some(identity) =>  users.findByIdentity(identity)
        case None => None
      }
      Future(InternalServerError(views.html.errorPage(request, sw.toString)(user)))
    }
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    if (request.path.contains("/api/")) {
      Future(NotFound(toJson(Map("status" -> "not found",
        "request" -> request.toString()))))
    } else {
      implicit val user = SecureSocial.currentUser(request) match {
        case Some(identity) => users.findByIdentity(identity)
        case None => None
      }
      Future(NotFound(views.html.errorPage(request, "Not found")(user)))
    }
  }

  override def onBadRequest(request: RequestHeader, error: String) = {
    if (request.path.contains("/api/")) {
      Future(BadRequest(toJson(Map("status" -> ("bad request"),
        "message" -> error,
        "request" -> request.toString()))))
    } else {
      implicit val user = SecureSocial.currentUser(request) match {
        case Some(identity) => users.findByIdentity(identity)
        case None => None
      }
      Future(BadRequest(views.html.errorPage(request, error)(user)))
    }
  }
}
