package models

import org.bson.types.ObjectId
import services.MongoSalatPlugin
import com.novus.salat.dao.{ ModelCompanion, SalatDAO }
import com.mongodb.casbah.Imports._
import MongoContext.context
import play.api.Play.current
import play.api.Logger


/**
 * Tracks application appearance settings (settable by admins).
 *
 * @author Constantinos Sophocleous
 *
 */

case class AppAppearance(
  id: ObjectId = new ObjectId,
  name: String = "default",
  displayedName: String = "Medici 2",
  welcomeMessage: String = "Welcome to Medici 2.0, a scalable data repository where you can share, organize and analyze data."  
  )

object AppAppearance extends ModelCompanion[AppAppearance, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[AppAppearance, ObjectId](collection = x.collection("app.appearance")) {}

  }
  
  def getDefault(): Option[AppAppearance] = {
    dao.findOne(MongoDBObject("name" -> "default"))match {
      case Some(conf) => Some(conf)
      case None => {
        val default = AppAppearance()
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
  
}