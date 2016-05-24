package models

import java.text.SimpleDateFormat
import java.util.Calendar
import services.SchedulerService
import services.DI

/**
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
