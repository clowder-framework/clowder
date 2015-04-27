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
    val users: UserService =  DI.injector.getInstance(classOf[UserService])
    val events: EventService =  DI.injector.getInstance(classOf[EventService])
    
    def runScheduledJobs() = {
      val curr_time = Calendar.getInstance().getTime()
      val minute = new SimpleDateFormat("m").format(curr_time)
      val hour = new SimpleDateFormat("H").format(curr_time)
      val day_of_week = new SimpleDateFormat("u").format(curr_time)
      val day_of_month = new SimpleDateFormat("d").format(curr_time)
      var emailJobs = getEmailJobs(minute.toInt, hour.toInt, day_of_week.toInt)
      sendEmailUser(emailJobs)
  }

  /**
  * Gets the events for each viewer and sends out emails
  */

  def sendEmailUser(userList: List[TimerJob]) = {
  	for (job <- userList){
  		job.parameters match {
  			case Some(id) => {
  				users.findById(id) match {
  					case Some(user) => {
  						user.email match {
  							case Some(email) => {	
  								job.lastJobTime match {
  									case Some(date) =>{
                    sendDigestEmail(email,events.getAllEventsByTime(user.followedEntities, date)) 
  									}
  									}
  								}
  							}
  						}
  					}
  				}
  				scheduler.updateLastRun("Digest[" + id + "]")
  			}
  		}
  	}

    /**
    * Sends and creates a Digest Email
    */

  def sendDigestEmail(email: String, events: List[Event]) = {
    var eventsList = events.sorted(Ordering.by((_: Event).created).reverse)
    val body = views.html.emailEvents(eventsList)
    val mail = use[MailerPlugin].email

    mail.setSubject("Clowder Email Digest")
    mail.setRecipient(email)
    mail.setFrom(Mailer.fromAddress)
    mail.send("", body.body)
   
    Logger.info("Email Sent")
  }

  /**
  * Gets the Jobs for this current time
  */

  def getEmailJobs (minute: Integer, hour: Integer, day_of_week: Integer) = {
	 var emailJobs = scheduler.getJobByTime(minute, hour, day_of_week)
	 emailJobs
  }



}

