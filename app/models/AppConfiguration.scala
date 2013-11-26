/**
 *
 */
package models

import org.bson.types.ObjectId
import java.util.Date
import com.novus.salat.dao.{ ModelCompanion, SalatDAO }
import services.MongoSalatPlugin
import com.mongodb.casbah.Imports._
import MongoContext.context
import play.api.Play.current
import services.Services
import play.api.Logger

/**
 * Tracks application wide configurations.
 *
 * @author Luigi Marini
 *
 */
case class AppConfiguration(
  id: ObjectId = new ObjectId,
  name: String = "default",
  theme: String = "bootstrap/bootstrap.css")

object AppConfiguration extends ModelCompanion[AppConfiguration, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[AppConfiguration, ObjectId](collection = x.collection("app.configuration")) {}

  }
  
  def getDefault(): Option[AppConfiguration] = {
    dao.findOne(MongoDBObject("name" -> "default")) match {
      case Some(conf) => Some(conf)
      case None => {
        val default = AppConfiguration()
        AppConfiguration.save(default)
        Some(default)
      }
    }
  }
  
  def setTheme(theme: String) {
    Logger.debug("Setting theme to " + theme)
    getDefault match {
      case Some(conf) => AppConfiguration.update(MongoDBObject("name" -> "default"), $set("theme" ->  theme), false, false, WriteConcern.Safe)
      case None => {}
    }
    
  }
}