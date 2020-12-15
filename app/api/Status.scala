package api

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.User
import play.api.libs.json.{JsValue, Json}
import services._
import services.mongodb.MongoSalatPlugin
import util.silhouette.auth.ClowderEnv

import scala.collection.mutable

/**
 * class that contains all status/version information about clowder.
 */
class Status @Inject()() extends ApiController {
  def version = UserAction(needActive=false) { implicit request =>
    Ok(Json.obj("version" -> "2.0"))
  }
}
