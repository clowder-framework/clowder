package models

import org.bson.types.ObjectId
import com.novus.salat.dao.{ ModelCompanion, SalatDAO }
import com.mongodb.casbah.Imports._
import play.api.Play.current
import play.api.Logger
import services.mongodb.MongoSalatPlugin
import services.mongodb.MongoContext


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

