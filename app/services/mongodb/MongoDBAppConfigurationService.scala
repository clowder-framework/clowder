package services.mongodb

import services.AppConfigurationService
import com.mongodb.casbah.Imports._
import scala.Some
import models.AppConfiguration
import play.api.Logger

/**
 * Created by lmarini on 2/21/14.
 */
class MongoDBAppConfigurationService extends AppConfigurationService {

  def getDefault(): Option[AppConfiguration] = {
    AppConfiguration.findOne(MongoDBObject("name" -> "default")) match {
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
      case Some(conf) => AppConfiguration.update(MongoDBObject("name" -> "default"), $set("theme" -> theme), false, false, WriteConcern.Safe)
      case None => {}
    }
  }

  def getTheme(): String = {
    getDefault match {
      case Some(appConf) => Logger.debug("Theme" + appConf.theme); appConf.theme
      case None => themes(0)
    }
  }
}
