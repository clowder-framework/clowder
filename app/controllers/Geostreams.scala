/**
 *
 */
package controllers

import play.api.Play._
import play.api.mvc.Controller
import api.WithPermission
import api.Permission
import services.PostgresPlugin
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.Logger

/**
 * View/Add/Remove Geostreams
 * 
 * @author Luigi Marini
 *
 */
object Geostreams extends Controller with SecuredController {

  var plugin = current.plugin[PostgresPlugin]

  val pluginNotEnabled = InternalServerError("Geostreaming plugin not enabled")

  def list() = SecuredAction(authorization=WithPermission(Permission.ListSensors)) { implicit request =>
    implicit val user = request.user
    plugin match {
      case Some(db) => {
        val json: JsValue = Json.parse(db.searchSensors(None, None).getOrElse("{}"))
        val sensorResult = json.validate[List[JsValue]]
        val list = sensorResult match {
          case JsSuccess(list : List[JsValue], _) => list
          case e: JsError => {
            Logger.debug("Errors: " + JsError.toFlatJson(e).toString())
            List()
          }
        }
        Ok(views.html.geostreams.list(list))
      }
      case None => pluginNotEnabled
    }
  }

  def map() = SecuredAction(authorization=WithPermission(Permission.ListSensors)) { implicit request =>
    implicit val user = request.user
    plugin match {
      case Some(db) => {
        val json: JsValue = Json.parse(db.searchSensors(None, None).getOrElse("{}"))
        val sensorResult = json.validate[List[JsValue]]
        val list = sensorResult match {
          case JsSuccess(list : List[JsValue], _) => list
          case e: JsError => {
            Logger.debug("Errors: " + JsError.toFlatJson(e).toString())
            List()
          }
        }
        Ok(views.html.geostreams.map(list))
      }
      case None => pluginNotEnabled
    }
  }

  def newSensor() = SecuredAction(authorization=WithPermission(Permission.CreateSensors)) { implicit request =>
    implicit val user = request.user
    plugin match {
      case Some(db) => Ok(views.html.geostreams.create())
      case None => pluginNotEnabled
    }
  }

  def sensor(id: String)= SecuredAction(authorization=WithPermission(Permission.ListSensors)) { implicit request =>
    plugin match {
      case Some(db) => {
        val sensor = Json.parse(db.getSensor(id).getOrElse("{}"))
        Ok(views.html.geostreams.sensor(sensor, (sensor \ "id").as[Int].toString))
      }
      case None => pluginNotEnabled
    }
  }

}