package services

import java.time.temporal.ChronoUnit
import java.time.{Duration, ZonedDateTime}
import java.util.Calendar

import akka.actor.{ActorSystem, Cancellable}
import javax.inject.{Inject, Singleton}
import models.{JobsScheduler, MetadataDefinition, Role, ServerStartTime}
import play.api.Logging
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * This class replaces the old `Global` and is executed at startup.
 * TODO: move to specific services
 *
 * @param configuration access application.conf
 * @param actorSystem schedule jobs using Akka
 * @param executionContext schedule jobs using Akka
 * @param lifecycle used to add onStopHook for cleanup
 * @param extractionBusService used to clean up ExtractionService TODO: move to that service class
 */
@Singleton
class StartUpService @Inject() (configuration: play.api.Configuration, actorSystem: ActorSystem,
                                lifecycle: ApplicationLifecycle, extractionBusService: ExtractionBusService)
                               (implicit executionContext: ExecutionContext) extends Logging {

  var extractorTimer: Cancellable = null
  var jobTimer: Cancellable = null
  var archivalTimer: Cancellable = null
  val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])
  val files: FileService = DI.injector.getInstance(classOf[FileService])

  ServerStartTime.startTime = Calendar.getInstance().getTime
  logger.debug("\n----Server Start Time----" + ServerStartTime.startTime + "\n \n")

  val users: UserService = DI.injector.getInstance(classOf[UserService])

  // set the default ToS version
  AppConfiguration.setDefaultTermsOfServicesVersion()

  // add all new admins
  users.updateAdmins()

  // create default roles
  if (users.listRoles().isEmpty) {
    logger.debug("Ensuring roles exist")
    users.updateRole(Role.Admin)
    users.updateRole(Role.Editor)
    users.updateRole(Role.Viewer)
  }

  // set default metadata definitions
  MetadataDefinition.registerDefaultDefinitions()

  val archiveEnabled = configuration.get[Boolean]("archiveEnabled")
  if (archiveEnabled && archivalTimer == null) {
    val archiveDebug = configuration.get[Boolean]("archiveDebug")
    val interval = if (archiveDebug) 5.minutes else 1.day

    // Determine time until next midnight
    val now = ZonedDateTime.now
    val midnight = now.truncatedTo(ChronoUnit.DAYS)
    val sinceLastMidnight = Duration.between(midnight, now).getSeconds
    val delay = if (archiveDebug) { 10.seconds } else {
      (Duration.ofDays(1).getSeconds - sinceLastMidnight) seconds
    }

    logger.info("Starting archival loop - first iteration in " + delay + ", next iteration after " + interval)
    archivalTimer = actorSystem.scheduler.schedule(delay, interval) {
      logger.info("Starting auto archive process...")
      files.autoArchiveCandidateFiles()
    }
  }

  if (jobTimer == null) {
    jobTimer = actorSystem.scheduler.schedule(0 minutes, 1 minutes) {
      JobsScheduler.runScheduledJobs()
    }
  }

  // Get database counts from appConfig; generate them if unavailable or user count = 0
  appConfig.getProperty[Long]("countof.bytes") match {
    case Some(filesBytes) =>
      case None => {
      // Reset byte count to zero before incrementing
      appConfig.resetCount('bytes)

      logger.info("Byte count not found in appConfig; scheduling database counting in 10s...")
      actorSystem.scheduler.scheduleOnce(10 seconds) {
        logger.debug("Initializing appConfig byte count...")
        val files: FileService = DI.injector.getInstance(classOf[FileService])

        // Store the byte count in appConfig so it can be fetched quickly later
        appConfig.incrementCount('bytes, files.bytes())
        logger.info("Initialized appConfig byte count")
      }
    }
  }

  logger.info("Application has started")


  lifecycle.addStopHook { () =>
    Future.successful {
      extractorTimer.cancel()
      jobTimer.cancel()
      extractionBusService.onStop()
      logger.info("Application shutdown")
    }
  }
}
