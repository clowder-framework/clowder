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
    (__ \ 'title).read[String] and
    (__ \ 'timestamp).read[String] and
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
      request.body.validate[(String, String, List[Double], JsValue)].map{ 
        case (title, timestamp, longlat, data) => 
          current.plugin[PostgresPlugin].foreach{
            _.add(title, formatter.parse(timestamp), Json.stringify(data), longlat(1), longlat(0))
          }
          Ok(toJson("success"))
      }.recoverTotal{
        e => BadRequest("Detected error:"+ JsError.toFlatJson(e))
      }
    }
  }
  
  def search() = Authenticated {
    Action { request =>
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => Ok(plugin.search)
        case None => InternalServerError(toJson("Geostreaming not enabled"))
      }
    }
  }
}