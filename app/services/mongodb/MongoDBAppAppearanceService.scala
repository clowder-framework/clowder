package services.mongodb

import services.AppAppearanceService
import models.AppAppearance
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.Imports._
import MongoContext.context
import play.api.Play.current
import play.api.Logger

class MongoDBAppAppearanceService extends AppAppearanceService {

  def getDefault(): Option[AppAppearance] = {
    AppAppearance.dao.findOne(MongoDBObject("name" -> "default"))match {
      case Some(conf) => Some(conf)
      case None => {
        val default = models.AppAppearance()
        AppAppearance.save(default)
        Some(default)
      }
    }
  }
  
  def setDisplayedName(displayedName: String) {
    Logger.debug("Setting displayed name to " + displayedName)
    getDefault match {
      case Some(conf) => AppAppearance.update(MongoDBObject("name" -> "default"), $set("displayedName" ->  displayedName), false, false, WriteConcern.Safe)
      case None => {}
    }    
  }
  
  def setWelcomeMessage(welcomeMessage: String) {
    Logger.debug("Setting welcome message to " + welcomeMessage)
    getDefault match {
      case Some(conf) => AppAppearance.update(MongoDBObject("name" -> "default"), $set("welcomeMessage" ->  welcomeMessage), false, false, WriteConcern.Safe)
      case None => {}
    }    
  }


  def setSensorsTitle(sensors: String) {
    Logger.debug("Setting sensors title to " + sensors)
    getDefault match {
      case Some(conf) => AppAppearance.update(MongoDBObject("name" -> "default"), $set("sensors" ->  sensors), false, false, WriteConcern.Safe)
      case None => {}
    }
  }

  def setSensorTitle(sensor: String) {
    Logger.debug("Setting sensor title to " + sensor)
    getDefault match {
      case Some(conf) => AppAppearance.update(MongoDBObject("name" -> "default"), $set("sensor" ->  sensor), false, false, WriteConcern.Safe)
      case None => {}
    }
  }
  
}

object AppAppearance extends ModelCompanion[AppAppearance, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[AppAppearance, ObjectId](collection = x.collection("app.appearance")) {}

  }
 }