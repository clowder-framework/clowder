/**
 *
 */
package controllers

import play.api.Play._
import play.api.mvc.Controller
import api.Permission
import services.PostgresPlugin
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.Logger

/**
 * View/Add/Remove Geostreams
 */
object Geostreams extends Controller with SecuredController {

  var plugin = current.plugin[PostgresPlugin]

  val pluginNotEnabled = InternalServerError("Geostreaming plugin not enabled")

  def list() = PermissionAction(Permission.ViewSensor) { implicit request =>
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

  def map() = PermissionAction(Permission.ViewSensor) { implicit request =>
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

  def newSensor() = PermissionAction(Permission.CreateSensor) { implicit request =>
    implicit val user = request.user
    plugin match {
      case Some(db) => Ok(views.html.geostreams.create())
      case None => pluginNotEnabled
    }
  }

  def edit(id: String)= PermissionAction(Permission.CreateSensor) { implicit request =>
    implicit val user = request.user
    plugin match {
      case Some(db) => {
        val sensor = Json.parse(db.getSensor(id).getOrElse("{}"))
        val stream_ids: JsValue = Json.parse(db.getSensorStreams(id).getOrElse("[]"))

        val streamsResult = stream_ids.validate[List[JsValue]]
        val list = streamsResult match {
          case JsSuccess(list : List[JsValue], _) => list
          case e: JsError => {
            Logger.debug("Errors: " + JsError.toFlatJson(e).toString())
            List()
          }
        }
        Logger.debug(list.toString)
        val streams = list.map { stream =>
          // val stream_id = (stream \ "stream_id").toString
          Json.parse(db.getStream((stream \ "stream_id").toString).getOrElse("{}"))
        }

        Ok(views.html.geostreams.edit(sensor, streams))
      }
      case None => pluginNotEnabled
    }
  }

}