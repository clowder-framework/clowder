package models

import org.joda.time.DateTime

import play.api.Play.current
import scala.concurrent.duration

import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import play.api.Play.current
import java.util.ArrayList
import play.api.libs.concurrent
import services.RabbitmqPlugin
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsObject
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import javax.inject.Inject

import services.SchedulerService
import services.UserService
import services.EventService
import services.DI

import scala.concurrent.Future
import services.ExtractionRequestsService
import java.net.InetAddress
import play.api.libs.ws.Response
import securesocial.core.providers.utils.Mailer
import com.typesafe.plugin._
import play.api.libs.concurrent.Akka
/**
 * @author Varun Kethineedi 
 * 
 *  Schedules Jobs 
 * 
 */

 /**
 * Runs from jobs collections job
 */

  object JobsScheduler {
    val scheduler: SchedulerService =  DI.injector.getInstance(classOf[SchedulerService])

    def runScheduledJobs() = {
      val curr_time = Calendar.getInstance().getTime()
      val minute = new SimpleDateFormat("m").format(curr_time)
      val hour = new SimpleDateFormat("H").format(curr_time)
      val day_of_week = new SimpleDateFormat("u").format(curr_time)
      val day_of_month = new SimpleDateFormat("d").format(curr_time)
      var emailJobs = getEmailJobs(minute.toInt, hour.toInt, day_of_week.toInt)
      Events.sendEmailUser(emailJobs)
    }

  /**
  * Gets the Jobs for this current time
  */
    def getEmailJobs (minute: Integer, hour: Integer, day_of_week: Integer) = {
      var emailJobs = scheduler.getJobByTime(minute, hour, day_of_week)
      emailJobs
    }

}
