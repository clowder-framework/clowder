package services
import models._
import play.api.libs.json.JsValue
import com.mongodb.casbah.Imports._

import com.novus.salat.dao.SalatMongoCursor
/**
 * Service to add a job to scheduler
 *
 */
 
trait SchedulerService {
	 
	 /**
	 * Lists all the scheduled jobs
	 */

	def listJobs(): List[TimerJob]

	 /**
	 * schedule jobs
	 */

	def scheduleJob(job: TimerJob)

	 /**
	 * find job by name
	 */

	def getJob(name: String): SalatMongoCursor[TimerJob]

	/**
	* Remove Job from Jobs Collection
	*/

	def deleteJob(name: String)

	/**
	* Add an email digest job
	*/

	def updateEmailJob(id : UUID, name: String, setting: String)

	/*
	* Check if job exists in collection
	*/
	def jobExists(name: String): Boolean

	/*
	* Update the time last job was ran
	*/

	def updateLastRun(name: String)

	/**
	* Get Job by the current time
	*/
	def getJobByTime(minute: Integer, hour: Integer, day: Integer): List[TimerJob]

}