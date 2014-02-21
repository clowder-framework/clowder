package models

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.Imports._
import MongoContext.context
import play.api.Play.current
import play.api.Logger
import services.mongodb.MongoSalatPlugin

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
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[AppConfiguration, ObjectId](collection = x.collection("app.configuration")) {}
  }
}