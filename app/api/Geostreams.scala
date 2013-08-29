/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.functional.syntax._
import java.util.Date
import play.api.Play.current
import services.PostgresPlugin
import java.text.SimpleDateFormat
import play.api.Logger
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future
import services.ElasticsearchPlugin

/**
 * Geostreaming endpoints. A geostream is a time and geospatial referenced 
 * sequence of datapoints.
 * 
 * @author Luigi Marini
 *
 */
object Geostreams extends Controller {

  val formatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'")
  
  implicit val rds = (
    (__ \ 'start_time).read[String] and
    (__ \ 'end_time).readNullable[String] and
    (__ \ 'geog \ 'coordinates).read[List[Double]] and
    (__ \ 'data).json.pick and
    (__ \ 'stream_id).read[String]
  ) tupled
  
  def createStream() = Authenticated {
    Action(parse.json) { request =>
      Logger.debug("Creating stream")
      Ok(toJson("success"))
    }
  }
  
  def addDatapoint(id: String) = Authenticated {
    Action(parse.json) { request =>
      Logger.info("Adding datapoint: " + request.body)
      request.body.validate[(String, Option[String], List[Double], JsValue, String)].map{ 
        case (start_time, end_time, longlat, data, streamId) => 
          current.plugin[PostgresPlugin] match {
            case Some(plugin) => {
              Logger.info("GEOSTREAM TIME: " + start_time + " " + end_time)
              val end_date = if (end_time.isDefined) Some(formatter.parse(end_time.get)) else None
              if (longlat.length == 3) {
                plugin.add(formatter.parse(start_time), end_date, Json.stringify(data), longlat(1), longlat(0), longlat(2), streamId)
              } else { 
            	plugin.add(formatter.parse(start_time), end_date, Json.stringify(data), longlat(1), longlat(0), 0.0, streamId)
              }
              Ok(toJson("success"))
            }
           case None => InternalServerError(toJson("Geostreaming not enabled"))
      }}.recoverTotal{
        e => Logger.debug("Error parsing json: " + e); BadRequest("Detected error:"+ JsError.toFlatJson(e))
      }
    }
  }
  
  def search(since: Option[String], until: Option[String], geocode: Option[String]) =
    Action { request =>
      Logger.debug("Search " + since + " " + until + " " + geocode)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => Ok(Json.prettyPrint(Json.parse(plugin.search(since, until, geocode))))
        case None => InternalServerError(toJson("Geostreaming not enabled"))
      }
    }
  
}