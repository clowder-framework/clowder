/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.mvc.Request
import play.api.mvc.AnyContent
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
import java.sql.Timestamp
import services.PostgresPlugin

/**
 * Geostreaming endpoints. A geostream is a time and geospatial referenced
 * sequence of datapoints.
 *
 * @author Luigi Marini
 *
 */
object Geostreams extends ApiController {

  val formatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssXXX")
  val pluginNotEnabled = InternalServerError(toJson("Geostreaming not enabled"))
  
  implicit val sensors = (
    (__ \ 'name).read[String] and
    (__ \ 'type).read[String] and
    (__ \ 'geometry \ 'coordinates).read[List[Double]] and
    (__ \ 'properties).read[JsValue]
  ) tupled
  
  implicit val streams = (
    (__ \ 'name).read[String] and
    (__ \ 'type).read[String] and
    (__ \ 'geometry \ 'coordinates).read[List[Double]] and
    (__ \ 'properties).read[JsValue] and
    (__ \ 'sensor_id).read[String]
  ) tupled
  
  implicit val datapoints = (
    (__ \ 'start_time).read[String] and
    (__ \ 'end_time).readNullable[String] and
    (__ \ 'type).read[String] and
    (__ \ 'geometry \ 'coordinates).read[List[Double]] and
    (__ \ 'properties).json.pick and
    (__ \ 'stream_id).read[String]
  ) tupled

  def createSensor() = SecuredAction(authorization=WithPermission(Permission.CreateSensors)) { request =>
      Logger.debug("Creating sensor")
      request.body.validate[(String, String, List[Double], JsValue)].map {
        case (name, geoType, longlat, metadata) => {
          current.plugin[PostgresPlugin] match {
            case Some(plugin) => {
              plugin.createSensor(name, geoType, longlat(1), longlat(0), longlat(2), Json.stringify(metadata))
              Ok(toJson("success"))
            }
            case None => pluginNotEnabled
          }
        }
      }.recoverTotal {
        e => BadRequest("Detected error:" + JsError.toFlatJson(e))
      }
  }

  def searchSensors(geocode: Option[String]) =
    Action { request =>
      Logger.debug("Searching sensors " + geocode)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.searchSensors(geocode) match {
            case Some(d) => Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
            case None => Ok(Json.parse("""{"status":"No data found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }
  
  def getSensor(id: String) =
    Action { request =>
      Logger.debug("Get sensor " + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.getSensor(id) match {
            case Some(d) => Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
            case None => Ok(Json.parse("""{"status":"No data found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }
  
  
  def getSensorStreams(id: String) =
    Action { request =>
      Logger.debug("Get sensor streams" + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.getSensorStreams(id) match {
            case Some(d) => Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
            case None => Ok(Json.parse("""{"status":"No data found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }
  
  
  def getSensorStatistics(id: String) =  
    Action { request =>
      Logger.debug("Get sensor statistics" + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          val dates = plugin.getSensorDateRange(id) match {
            case Some(d) => d
            case None => """{}"""
          }
          val parameters = plugin.getSensorParameters(id) match {
            case Some(params) => params
            case None => """{"parameters":[]}"""
          }
          val json = Json.obj(
        		  "range" -> Json.parse(dates),
        		  "parameters" -> Json.parse(parameters) \ "parameters"
        		  )
          Ok(jsonp(Json.prettyPrint(json), request)).as("application/json")
        }
        case None => pluginNotEnabled
      }
  }
  
  def createStream() = SecuredAction(authorization=WithPermission(Permission.CreateSensors)) { request =>
      Logger.info("Creating stream: " + request.body)
      request.body.validate[(String, String, List[Double], JsValue, String)].map {
        case (name, geoType, longlat, metadata, sensor_id) => {
          current.plugin[PostgresPlugin] match {
            case Some(plugin) => {
              val id = plugin.createStream(name, geoType, longlat(1), longlat(0), longlat(2), Json.stringify(metadata), sensor_id)
              Ok(toJson(Json.obj("status"->"ok","id"->id)))              
            }
            case None => pluginNotEnabled
          }
        }
      }.recoverTotal {
        e => Logger.error(e.toString); BadRequest("Detected error:" + JsError.toFlatJson(e))
      }
  }

  def searchStreams(geocode: Option[String]) =
    Action { request =>
      Logger.debug("Searching stream " + geocode)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.searchStreams(geocode) match {
            case Some(d) => Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
            case None => Ok(Json.parse("""{"status":"No data found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }
  
  def getStream(id: String) =
    Action { request =>
      Logger.debug("Get stream " + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.getStream(id) match {
            case Some(d) => Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
            case None => Ok(Json.parse("""{"status":"No stream found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }
  
  def deleteStream(id: String) = Authenticated {
    Action(parse.empty) { request =>
      Logger.debug("Delete stream " + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          if (plugin.deleteStream(id.toInt)) Ok(Json.parse("""{"status":"ok"}""")).as("application/json")
          else Ok(Json.parse("""{"status":"error"}""")).as("application/json")
        }
        case None => pluginNotEnabled
      }
    }
  }
  
  def deleteAll() = Authenticated {
    Action(parse.empty) { request =>
      Logger.debug("Drop all")
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          if (plugin.dropAll()) Ok(Json.parse("""{"status":"ok"}""")).as("application/json")
          else Ok(Json.parse("""{"status":"error"}""")).as("application/json")
        }
        case None => pluginNotEnabled
      }
    }
  }
  
  def counts() =  Action { request =>
      Logger.debug("Counting entries")
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.counts() match {
          	case (sensors, streams, datapoints) => Ok(toJson(Json.obj("sensors"->sensors,"streams"->streams,"datapoints"->datapoints))).as("application/json")
          	case _ => Ok(Json.parse("""{"status":"error"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }

  def addDatapoint() = Authenticated {
    Action(parse.json) { request =>
      Logger.info("Adding datapoint: " + request.body)
      request.body.validate[(String, Option[String], String, List[Double], JsValue, String)].map {
        case (start_time, end_time, geoType, longlat, data, streamId) =>
          current.plugin[PostgresPlugin] match {
            case Some(plugin) => {
              Logger.info("GEOSTREAM TIME: " + start_time + " " + end_time)
              val start_timestamp = new Timestamp(formatter.parse(start_time).getTime())
              val end_timestamp = if (end_time.isDefined) Some(new Timestamp(formatter.parse(end_time.get).getTime())) else None
              if (longlat.length == 3) {
                plugin.addDatapoint(start_timestamp, end_timestamp, geoType, Json.stringify(data), longlat(1), longlat(0), longlat(2), streamId)
              } else {
                plugin.addDatapoint(start_timestamp, end_timestamp, geoType, Json.stringify(data), longlat(1), longlat(0), 0.0, streamId)
              }
              Ok(toJson("success"))
            }
            case None => pluginNotEnabled
          }
      }.recoverTotal {
        e => Logger.debug("Error parsing json: " + e); BadRequest("Detected error:" + JsError.toFlatJson(e))
      }
    }
  }

  def searchDatapoints(since: Option[String], until: Option[String], geocode: Option[String], stream_id: Option[String], sources: List[String], attributes: List[String], format: String) =
    Action { request =>
      Logger.debug("Search " + since + " " + until + " " + geocode)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.searchDatapoints(since, until, geocode, stream_id, sources, attributes) match {
            case Some(d) => {
              if (format == "csv") {
                val configuration = play.api.Play.configuration
                val hideprefix = configuration.getBoolean("json2csv.hideprefix").getOrElse(false)
                val ignore = configuration.getString("json2csv.ignore").getOrElse("").split(",")
                val seperator = configuration.getString("json2csv.seperator").getOrElse("|")
                val fixgeometry = configuration.getBoolean("json2csv.fixgeometry").getOrElse(true)
                Ok(jsonToCSV(Json.parse(d), ignore, hideprefix, seperator, fixgeometry)).as("text/csv")
              } else {
                Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
              }
            }
            case None => Ok(Json.toJson("""{"status":"No data found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }

  def getDatapoint(id: String) =
    Action { request =>
      Logger.debug("Get datapoint " + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          plugin.getDatapoint(id) match {
            case Some(d) => Ok(jsonp(Json.prettyPrint(Json.parse(d)), request)).as("application/json")
            case None => Ok(Json.parse("""{"status":"No stream found"}""")).as("application/json")
          }
        }
        case None => pluginNotEnabled
      }
    }

  def jsonp(json: String, request: Request[AnyContent]) = {
    request.getQueryString("callback") match {
      case Some(callback) => callback + "(" + json + ");"
      case None => json
    }
  }

  // ----------------------------------------------------------------------
  // Convert JSON data to CSV data.
  // This code is generic and could be moved to special module, right now
  // it is used to take a geostream output and convert it to CSV.
  // ----------------------------------------------------------------------
  /**
   * Returns the JSON formated as CSV.
   *
   * This will take the first json element and create a header from this. All
   * additional rows will be parsed based on this first header. Any fields not
   * in the first row, will not be outputed.
   * @param data the json to be converted to CSV.
   * @param ignore fields to ignore.
   * @param hideprefix set this to true to not print the prefix in header.
   * @returns csv formated JSON.
   */
  def jsonToCSV(data: JsValue, ignore: Array[String] = Array[String](), hidePrefix: Boolean = false, prefixSeperator: String = " - ", fixgeometry:Boolean = true) = {
    val values = data.as[List[JsObject]]
    val header = values(0)

    var row = None
    var result = ""
    result += printHeader(header, "", ignore, prefixSeperator, hidePrefix, fixgeometry) + "\n"
    for(row <- values)
    	result += printRow(header, "", ignore, prefixSeperator, row) + "\n"
    result
  }
  
  /**
   * Helper function to print header of JSON Object.
   */
  def printHeader(header: JsObject, prefix: String, ignore: Array[String], prefixSeperator: String, hidePrefix: Boolean, fixgeometry:Boolean):String = {
    var result = ""
    for(f <- header.fields if !(ignore contains printKey(prefix, f._1, prefixSeperator))) {
      f._2 match {
        case x: JsArray => result += "," + printHeader(x, printKey(prefix, f._1, prefixSeperator), ignore, prefixSeperator, hidePrefix, fixgeometry)
	    case x: JsObject => result += "," + printHeader(x, printKey(prefix, f._1, prefixSeperator), ignore, prefixSeperator, hidePrefix, fixgeometry)
	    case _ => if (hidePrefix) {
                    result += ",\"" + f._1.toString + "\""
                  } else {
                    result += ",\"" + printKey(prefix, f._1, prefixSeperator) + "\""
                  }
      }
    }
    result.substring(1)
  }

  /**
   * Helper function to print header of JSON Array.
   */
  def printHeader(header: JsArray, prefix: String, ignore: Array[String], prefixSeperator: String, hidePrefix: Boolean, fixgeometry:Boolean):String = {
    var result = ""
    // special case for geometry
    if (fixgeometry && prefix.endsWith("geometry" + prefixSeperator + "coordinates") && ((header.value.length == 2) || (header.value.length == 3))) {
      if (hidePrefix) {
        result += ",\"longitude\",\"latitude\""
        if (header.value.length == 3) {
          result += ",\"altitude\""
        }
      } else {
        result += ",\"" +  printKey(prefix, "longitude", prefixSeperator) + "\""
        result += ",\"" +  printKey(prefix, "latitude", prefixSeperator) + "\""
        if (header.value.length == 3) {
          result += ",\"" +  printKey(prefix, "altitude", prefixSeperator) + "\""
        }
      }
    } else {
      header.value.indices.foreach(i => header(i) match {
        case x: JsArray => result += "," + printHeader(x, printKey(prefix, i, prefixSeperator), ignore, prefixSeperator, hidePrefix, fixgeometry)
        case x: JsObject => result += "," + printHeader(x, printKey(prefix, i, prefixSeperator), ignore, prefixSeperator, hidePrefix, fixgeometry)
        case _ => if (hidePrefix) {
                    result += ",\"" + i.toString + "\""
                  } else {
                    result += ",\"" + printKey(prefix, i, prefixSeperator) + "\""
                  }
      })      
    }
    result.substring(1)
  }
  
  /**
   * Helper function to convert a key in json to a header key.
   */
  def printKey(prefix: String, key: Any, prefixSeperator: String) = {
    if (prefix == "")
       key.toString
     else
       prefix.+(prefixSeperator).+(key.toString)
  }

  /**
   * Helper function to print data row of JSON Object.
   */
  def printRow(header: JsObject, prefix: String, ignore: Array[String], prefixSeperator: String, row: JsObject) : String = {
    var result = ""
    for(f <- header.fields if !(ignore contains printKey(prefix, f._1, prefixSeperator))) {
      (f._2, row.\(f._1)) match {
        case (x: JsArray, y: JsArray) => result += "," + printRow(x, printKey(prefix, f._1, prefixSeperator), ignore, prefixSeperator,y)
        case (x: JsObject, y: JsObject) => result += "," +printRow(x, printKey(prefix, f._1, prefixSeperator), ignore, prefixSeperator,y)
        case (x, y) => result += "," + y.toString
      }
    }
    result.substring(1)
  }

  /**
   * Helper function to print data row of JSON Array.
   */
  def printRow(header: JsArray, prefix: String, ignore: Array[String], prefixSeperator: String, row: JsArray) : String  = {
    var result = ""
    header.value.indices.foreach(i =>
      (header(i), row(i)) match {
        case (x: JsArray, y: JsArray) => result += "," + printRow(x, printKey(prefix, i, prefixSeperator), ignore, prefixSeperator, y)
        case (x: JsObject, y: JsObject) => result += "," + printRow(x, printKey(prefix, i, prefixSeperator), ignore, prefixSeperator,y)
        case (_: JsString, y: JsString) => result += ",\"" + y.toString + "\""
        case (x, y) => result += "," + y.toString
    })
    result.substring(1)
  }
}
