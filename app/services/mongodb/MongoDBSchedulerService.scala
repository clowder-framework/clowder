package services.mongodb

import models._
import java.util.Date
import services.SchedulerService
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import play.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatMongoCursor

/**
 * Use MongoDB to store schedules
 */
class MongoDBSchedulerService extends SchedulerService {

  def listJobs(): List[TimerJob] = {
    (for (job <- Jobs.find(MongoDBObject())) yield job).toList
  }

  def scheduleJob(job: TimerJob) = {
    Jobs.insert(job);
  }

  def getJob(name: String): SalatMongoCursor[TimerJob] = {
    Jobs.find(MongoDBObject("name" -> name))
  }

   def jobExists(name: String): Boolean = {
    var checkJob = false
    var jobList = getJob(name)
    for (job <- jobList){
      checkJob = true 
    }
    return checkJob
  }


  def deleteJob(name: String) = {
    Jobs.remove(MongoDBObject("name" -> name))
  }


  def updateJobTime(name: String, minute: Option[Integer], hour: Option[Integer], day_of_week: Option[Integer], freq: Option[String]) = {
    if (minute == None){
      Jobs.dao.update(MongoDBObject("name" -> name), $unset("minute"))
    }
    else {
      Jobs.dao.update(MongoDBObject("name" -> name), $set("minute" -> minute))
    }

    if (hour == None){
      Jobs.dao.update(MongoDBObject("name" -> name), $unset("hour"))
    }
    else {
      Jobs.dao.update(MongoDBObject("name" -> name), $set("hour" -> hour))
    }


    if (day_of_week == None){
      Jobs.dao.update(MongoDBObject("name" -> name), $unset("day_of_week"))
    }
    else {
      Jobs.dao.update(MongoDBObject("name" -> name), $set("day_of_week" -> day_of_week))
    }


    Jobs.dao.update(MongoDBObject("name" -> name), $set("frequency" -> freq))
  }
  

  def updateEmailJob(id: UUID, name: String, setting: String) = {
    if (jobExists(name) == false) {
      Jobs.insert(new TimerJob(name, None, None, None, None, Option("EmailDigest"), Option(id), None, Option(new Date())))
    }
    if (setting == "none"){
      deleteJob(name)
    }
    else if (setting == "hourly"){
      updateJobTime(name, Option(0), None, None, Option(setting))
    }
    else if (setting == "daily"){
      updateJobTime(name, Option(0), Option(7), None, Option(setting))
    }
    else {
      updateJobTime(name, Option(0), Option(7), Option(1), Option(setting))
    }
  }

  def updateLastRun(name: String) = {
    Jobs.dao.update(MongoDBObject("name" -> name), $set("lastJobTime" -> new Date()))
  }



  def getJobByTime(minute: Integer, hour: Integer, day: Integer): List[TimerJob] ={
    var jobs = Jobs.find(  $and( $and( $or( $and( "minute" $exists true, MongoDBObject("minute" -> minute)), "minute" $exists false), $or( $and( "hour" $exists true, MongoDBObject("hour" -> hour)), "hour" $exists false)), $or( $and( "day_of_week" $exists true, MongoDBObject("day_of_week" -> day)), "day_of_week" $exists false)))
    var jobList = (for (job <- jobs) yield job).toList
    jobList
  }


}

  object Jobs extends ModelCompanion[TimerJob, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[TimerJob, ObjectId](collection = x.collection("jobs")) {}
  }
}