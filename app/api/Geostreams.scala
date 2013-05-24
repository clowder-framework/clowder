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

/**
 * Geostreaming endpoints. A geostream is a time and geospatial referenced 
 * sequence of datapoints.
 * 
 * @author Luigi Marini
 *
 */
object Geostreams extends Controller {

  val formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
  
  implicit val rds = (
    (__ \ 'start_time).read[String] and
    (__ \ 'end_time).readNullable[String] and
    (__ \ 'geog \ 'coordinates).read[List[Double]] and
    (__ \ 'data).json.pick
  ) tupled
  
  def createStream() = Authenticated {
    Action(parse.json) { request =>
      Ok(toJson("success"))
    }
  }
  
  def addDatapoint(id: String) = Authenticated {
    Action(parse.json) { request =>
      request.body.validate[(String, Option[String], List[Double], JsValue)].map{ 
        case (start_time, end_time, longlat, data) => 
          current.plugin[PostgresPlugin].foreach{
            Logger.info("GEOSTREAM TIME: " + start_time + " " + end_time)
            val end_date = if (end_time.isDefined) Some(formatter.parse(end_time.get)) else None
            if (longlat.length == 3) {
            	_.add(formatter.parse(start_time), end_date, Json.stringify(data), longlat(1), longlat(0), longlat(2))
            } else { 
            	_.add(formatter.parse(start_time), end_date, Json.stringify(data), longlat(1), longlat(0), 0.0)
            }
          }
          Ok(toJson("success"))
      }.recoverTotal{
        e => BadRequest("Detected error:"+ JsError.toFlatJson(e))
      }
    }
  }
  
  def search(since: Option[String], until: Option[String], geocode: Option[String]) =
    Action { request =>
      Logger.debug("Search " + since + " " + until + " " + geocode)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => Ok(plugin.search(since, until, geocode))
        case None => InternalServerError(toJson("Geostreaming not enabled"))
      }
    }
  
}