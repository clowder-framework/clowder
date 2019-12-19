/**
 *
 */
package api

import java.io.{File, PrintStream}
import java.security.MessageDigest

import _root_.util.{Parsers, PeekIterator}
import org.joda.time.{DateTime, IllegalInstantException}
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import play.api.mvc.{Request, SimpleResult}
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.functional.syntax._
import play.api.Play.current
import java.text.SimpleDateFormat

import play.api.Logger
import java.sql.Timestamp

import play.filters.gzip.Gzip
import services.PostgresPlugin

import scala.collection.mutable.ListBuffer
import play.api.libs.iteratee.{Enumeratee, Enumerator}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import services.AppConfiguration

/**
 * Geostreaming endpoints. A geostream is a time and geospatial referenced
 * sequence of datapoints.
 */
object Geostreams extends ApiController {

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

  implicit val sensorsGeoJSON = (
    (__ \ 'name).read[String] and
      (__ \ 'type).read[String] and
      (__ \ 'geometry).read[JsValue] and
      (__ \ 'properties).read[JsValue]
    ) tupled

  implicit val streamsGeoJSON = (
    (__ \ 'name).read[String] and
      (__ \ 'type).read[String] and
      (__ \ 'geometry).read[JsValue] and
      (__ \ 'properties).read[JsValue] and
      (__ \ 'sensor_id).read[String]
    ) tupled

  implicit val datapointsGeoJSON = (
    (__ \ 'start_time).read[String] and
      (__ \ 'end_time).readNullable[String] and
      (__ \ 'type).read[String] and
      (__ \ 'geometry).read[JsValue] and
      (__ \ 'properties).json.pick and
      (__ \ 'stream_id).read[String]
    ) tupled

  implicit val datapointsBulk = (
    (__ \ 'start_time).read[String] and
      (__ \ 'end_time).readNullable[String] and
      (__ \ 'type).read[String] and
      (__ \ 'properties).read[JsValue] and
      (__ \ 'geometry).read[JsValue]
    ) tupled


  def createSensor() = PermissionAction(Permission.AddGeoStream)(parse.json) { implicit request =>
    Logger.debug("Creating sensor")
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        request.body.validate[(String, String, List[Double], JsValue)].map {
          case (name, geoType, longlat, metadata) => {
            val geojson = createGeoJson(List(longlat(1),longlat(0),longlat(2)))
            plugin.createSensor(name, geoType, geojson, Json.stringify(metadata)) match {
              case Some(d) => jsonp(d, request)
              case None => BadRequest(s"Failed to create sensor $name")
            }
          }
        }.recoverTotal {
          e => {
            // NEW VERSION; geometry is GeoJSON
            request.body.validate[(String, String, JsValue, JsValue)].map {
              case (name, geoType, geom, metadata) =>
                plugin.createSensor(name, geoType, geom, Json.stringify(metadata)) match {
                  case Some(d) => jsonp(d, request)
                  case None => BadRequest(s"Failed to create sensor $name from GeoJSON")
                }
            }.recoverTotal {
              e => {
                Logger.error("Error parsing json: " + e);
                BadRequest("Failed to create sensor:" + JsError.toFlatJson(e))
              }
            }
          }
        }
      }
      case _ => pluginNotEnabled
    }
  }

  def updateSensorMetadata(id: String) = PermissionAction(Permission.CreateSensor)(parse.json) { implicit request =>
    Logger.debug("Updating sensor")
    request.body.validate[(JsValue)].map {
      case (data) => {
        current.plugin[PostgresPlugin] match {
          case Some(plugin) if plugin.isEnabled => {
            plugin.updateSensorMetadata(id, Json.stringify(data \ "properties")) match {
              case Some(d) => {
                plugin.updateSensorGeometry(id, Json.stringify(data \ "geometry")) match {
                  case Some(d2) => {
                    jsonp(d2, request)
                  }
                  case None => jsonp(Json.parse("""{"status":"Failed to update sensor"}"""), request)
                }
              }
              case None => jsonp(Json.parse("""{"status":"Failed to update sensor"}"""), request)
            }

          }
          case _ => pluginNotEnabled
        }
      }
    }.recoverTotal {
      e => BadRequest("Detected error:" + JsError.toFlatJson(e))
    }
  }

  def patchStreamMetadata(id: String) = PermissionAction(Permission.CreateSensor)(parse.json) { implicit request =>
    Logger.debug("Updating stream")
    request.body.validate[(JsValue)].map {
      case (data) => {
        current.plugin[PostgresPlugin] match {
          case Some(plugin) if plugin.isEnabled => {
            plugin.patchStreamMetadata(id, Json.stringify(data)) match {
              case Some(d) => jsonp(d, request)
              case None => jsonp(Json.parse("""{"status":"Failed to update stream"}"""), request)
            }

          }
          case _ => pluginNotEnabled
        }
      }
    }.recoverTotal {
      e => BadRequest("Detected error:" + JsError.toFlatJson(e))
    }
  }

  def searchSensors(geocode: Option[String], sensor_name: Option[String], geojson: Option[String]) = PermissionAction(Permission.ViewGeoStream)
  { implicit request =>
    Logger.debug("Searching sensors " + geocode + " " + sensor_name)
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        geojson match {
          case Some(gj) => {
            plugin.searchSensorsGeoJson(geojson, sensor_name) match {
              case Some(d) => jsonp(d, request)
              case None => jsonp(Json.parse("""{"status":"No data found"}"""), request)
            }
          }
          case None => {
            plugin.searchSensors(geocode, sensor_name) match {
              case Some(d) => jsonp(d, request)
              case None => jsonp(Json.parse("""{"status":"No data found"}"""), request)
            }
          }
        }
      }
      case _ => pluginNotEnabled
    }
  }

  def getSensor(id: String) = PermissionAction(Permission.ViewGeoStream) { implicit request =>
      Logger.debug("Get sensor " + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) if plugin.isEnabled => {
          plugin.getSensor(id) match {
            case Some(d) => jsonp(d, request)
            case None => jsonp(Json.parse("""{"status":"No data found"}"""), request)
          }
        }
        case _ => pluginNotEnabled
      }
    }

  def getSensorStreams(id: String) = PermissionAction(Permission.ViewGeoStream) { implicit request =>
      Logger.debug("Get sensor streams" + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) if plugin.isEnabled => {
          plugin.getSensorStreams(id) match {
            case Some(d) => jsonp(d, request)
            case None => jsonp("""{"status":"No data found"}""", request)
          }
        }
        case _ => pluginNotEnabled
      }
    }

  def updateStatisticsSensor(id: String) = PermissionAction(Permission.AddGeoStream) { implicit request =>
    Logger.debug("update sensor statistics for " + id)
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        plugin.updateSensorStats(Some(id))
        jsonp("""{"status":"updated"}""", request)
      }
      case _ => pluginNotEnabled
    }
  }

  def updateStatisticsStream(id: String) = PermissionAction(Permission.AddGeoStream) { implicit request =>
    Logger.debug("update stream statistics for " + id)
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        plugin.updateStreamStats(Some(id))
        jsonp("""{"status":"updated"}""", request)
      }
      case _ => pluginNotEnabled
    }
  }

  def updateStatisticsStreamSensor() = PermissionAction(Permission.AddGeoStream) { implicit request =>
    Logger.debug("update all sensor/stream statistics")
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        plugin.updateSensorStats(None)
        jsonp("""{"status":"updated"}""", request)
      }
      case _ => pluginNotEnabled
    }
  }

  def getSensorStatistics(id: String) = PermissionAction(Permission.ViewGeoStream) { implicit request =>
      Logger.debug("Get sensor statistics " + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) if plugin.isEnabled => {
          val json = plugin.getSensorStats(id) match {
            case Some(d) => {
              val data = Json.parse(d)
              Json.obj(
                "range" -> Map[String, JsValue]("min_start_time" -> data \ "min_start_time",
                                                "max_end_time" -> data \ "max_end_time"),
                "parameters" -> data \ "parameters"
              )
            }
            case None => Json.obj("range" -> Map.empty[String, String], "parameters" -> Array.empty[String])
          }
          jsonp(json, request)
        }
        case _ => pluginNotEnabled
      }
  }

  def createStream() = PermissionAction(Permission.AddGeoStream)(parse.json) { implicit request =>
    Logger.debug("Creating stream: " + request.body)

    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        request.body.validate[(String, String, List[Double], JsValue, String)].map {
          case (name, geoType, longlat, metadata, sensor_id) => {
            val geojson = createGeoJson(longlat)
            plugin.createStream(name, geoType, geojson, Json.stringify(metadata), sensor_id) match {
              case Some(d) => jsonp(d, request)
              case None => BadRequest(s"Failed to create a stream $name")
            }
          }

        }.recoverTotal {
          e => {
            // NEW VERSION; geometry is GeoJSON
            request.body.validate[(String, String, JsValue, JsValue, String)].map {
              case (name, geoType, geom, metadata, sensor_id) =>
                plugin.createStream(name, geoType, geom, Json.stringify(metadata), sensor_id) match {
                  case Some(d) => jsonp(d, request)
                  case None => BadRequest(s"Failed to create stream $name from GeoJSON")
                }
            }.recoverTotal {
              e => {
                Logger.error("Error parsing json: " + e);
                BadRequest("Failed to create stream:" + JsError.toFlatJson(e))
              }
            }
          }
        }
      }
      case _ => pluginNotEnabled
    }
  }

  def searchStreams(geocode: Option[String], stream_name: Option[String], geojson: Option[String]) =  PermissionAction(Permission.ViewGeoStream) { implicit request =>
      Logger.debug("Searching stream " + geocode)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) if plugin.isEnabled => {
          geojson match {
            case Some(gj) => {
              plugin.searchStreamsGeoJson(geojson, stream_name) match {
                case Some(d) => jsonp(d, request)
                case None => jsonp("""{"status":"No data found"}""", request)
              }
            }
            case None => {
              plugin.searchStreams(geocode, stream_name) match {
                case Some(d) => jsonp(d, request)
                case None => jsonp("""{"status":"No data found"}""", request)
              }
            }
          }

        }
        case _ => pluginNotEnabled
      }
    }

  def getStream(id: String) = PermissionAction(Permission.ViewGeoStream) { implicit request =>
      Logger.debug("Get stream " + id)
      current.plugin[PostgresPlugin] match {
        case Some(plugin) if plugin.isEnabled => {
          plugin.getStream(id) match {
            case Some(d) => jsonp(d, request)
            case None => jsonp("""{"status":"No stream found"}""", request)
          }
        }
        case _ => pluginNotEnabled
      }
    }

  def deleteStream(id: String) = PermissionAction(Permission.DeleteGeoStream) { implicit request =>
    Logger.debug("Delete stream " + id)
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        if (plugin.deleteStream(id.toInt)) jsonp("""{"status":"ok"}""", request)
        else jsonp("""{"status":"error"}""", request)
      }
      case _ => pluginNotEnabled
    }
  }

  def deleteSensor(id: String) = PermissionAction(Permission.DeleteGeoStream) { implicit request =>
    Logger.debug("Delete sensor " + id)
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        if (plugin.deleteSensor(id.toInt)) jsonp("""{"status":"ok"}""", request)
        else jsonp("""{"status":"error"}""", request)
      }
      case _ => pluginNotEnabled
    }
  }

  def deleteDatapoint(id: String) = PermissionAction(Permission.DeleteGeoStream) { implicit request =>
    Logger.debug("Delete datapoint " + id)
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        if (plugin.deleteDatapoint(id.toInt)) jsonp("""{"status":"ok"}""", request)
        else jsonp("""{"status":"error"}""", request)
      }
      case _ => pluginNotEnabled
    }
  }

  def deleteAll() = PermissionAction(Permission.DeleteGeoStream) { implicit request =>
    Logger.debug("Drop all")
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        if (plugin.dropAll()) jsonp("""{"status":"ok"}""", request)
        else jsonp("""{"status":"error"}""", request)
      }
      case _ => pluginNotEnabled
    }
  }

  def counts() = PermissionAction(Permission.ViewGeoStream) { implicit request =>
      Logger.debug("Counting entries")
      current.plugin[PostgresPlugin] match {
        case Some(plugin) if plugin.isEnabled => {
          plugin.counts() match {
            case (sensors, streams, datapoints) => jsonp(Json.obj("sensors"->sensors,"streams"->streams,"datapoints"->datapoints), request)
            case _ => jsonp("""{"status":"error"}""", request)
          }
        }
        case _ => pluginNotEnabled
      }
    }

  def addDatapoint(invalidateCache: Boolean)  = PermissionAction(Permission.AddGeoStream)(parse.json) { implicit request =>
    Logger.debug("Adding datapoint: " + request.body)
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
    var failedToParse: Option[JsError] = None;

    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        // OLD VERSION; geometry is a list of 2 or 3 doubles for POINT (X,Y) or POINT Z (X,Y,Z)
        request.body.validate[(String, Option[String], String, List[Double], JsValue, String)].map {
          case (start_time, end_time, geoType, longlat, data, streamId) =>
                val start_timestamp = new Timestamp(formatter.parse(start_time).getTime())
                val end_timestamp = if (end_time.isDefined) Some(new Timestamp(formatter.parse(end_time.get).getTime())) else None
                val geojson = createGeoJson(longlat)
                plugin.addDatapoint(start_timestamp, end_timestamp, geoType, Json.stringify(data), geojson, streamId) match {
                  case Some(d) => {
                    if (invalidateCache) {
                      cacheInvalidate(((Json.parse(d)) \ "sensor_id").asOpt[String], None)
                    }
                    jsonp(d, request)
                  }
                  case None => BadRequest("Failed to create datapoint from coordinate list")
                }
        }.recoverTotal { e => {
          // NEW VERSION; geometry is GeoJSON
          request.body.validate[(String, Option[String], String, JsValue, JsValue, String)].map {
            case (start_time, end_time, geoType, geom, data, streamId) =>
              val start_timestamp = new Timestamp(formatter.parse(start_time).getTime())
              val end_timestamp = if (end_time.isDefined) Some(new Timestamp(formatter.parse(end_time.get).getTime())) else None
              plugin.addDatapoint(start_timestamp, end_timestamp, geoType, Json.stringify(data), geom, streamId) match {
                case Some(d) => {
                  if (invalidateCache) {
                    cacheInvalidate(((Json.parse(d)) \ "sensor_id").asOpt[String], None)
                  }
                  jsonp(d, request)
                }
                case None => BadRequest("Failed to create datapoint from GeoJSON")
              }
          }.recoverTotal {
            e => {
              Logger.debug("Error parsing json: " + e);
              BadRequest("Failed to create datapoint:" + JsError.toFlatJson(e))
            }
          }}
        }
      }
      case _ => pluginNotEnabled
    }
  }

  def addDatapoints(invalidateCache: Boolean)  = PermissionAction(Permission.AddGeoStream)(parse.json) { implicit request =>
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        val datapoints = (request.body \ "datapoints").as[List[(String, Option[String], String, JsValue, JsValue)]]
        val stream_id = (request.body \ "stream_id").as[String]
        plugin.addDatapoints(datapoints, stream_id) match {
          case Some(d) => jsonp(d, request)
          case None => BadRequest("Failed to create bulk datapoints")
        }
      }
    }
  }

  // need to create the following datastructure
  // {
  //   sensor_name: <sensor name>
  //   properties: [
  //     <property>: [
  //       <time specific fields>
  //       depth: <depth>
  //       label: <label based on time>
  //       source: [ sources ]
  //       average: <average value>
  //       count: <raw.length>
  //       doubles: <all raw double data> iff (keepRaw)
  //       strings: <all non double data> iff (keepRaw)
  //     ]
  //   ]
  // }
  def binDatapoints(time: String, depth: Double, keepRaw: Boolean, since: Option[String], until: Option[String], geocode: Option[String], stream_id: Option[String], sensor_id: Option[String], sources: List[String], attributes: List[String]) =  PermissionAction(Permission.ViewGeoStream) { implicit request =>
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        val description = Json.obj("time" -> time,
          "depth" -> depth,
          "since" -> since.getOrElse("").toString,
          "until" -> until.getOrElse("").toString,
          "geocode" -> geocode.getOrElse("").toString,
          "stream_id" -> stream_id.getOrElse("").toString,
          "sensor_id" -> sensor_id.getOrElse("").toString,
          "sources" -> Json.toJson(sources),
          "attributes" -> Json.toJson(attributes))

        cacheFetch(description) match {
          case Some(data) => jsonp(data.through(Enumeratee.map(new String(_))), request)
          case None => {
            val raw = new PeekIterator(plugin.searchDatapoints(since, until, geocode, stream_id, sensor_id, sources, attributes, true))
            val data = new Iterator[JsObject] {
              var nextObject: Option[JsObject] = None

              def hasNext = {
                if (nextObject.isDefined) {
                  true
                } else {
                  nextObject = binData(raw, time, depth, keepRaw)
                  nextObject.isDefined
                }
              }

              def next() = {
                if (hasNext) {
                  val x = nextObject.get
                  nextObject = None
                  x
                } else {
                  null
                }
              }
            }

            jsonp(cacheWrite(description, formatResult(data, "json")), request)
          }
        }
      }
      case _ => pluginNotEnabled
    }
  }

  /**
   * Compute the average value for a sensor. This will return a single
   * sensor with the average values for that sensor. The next object is
   * waiting in the peekIterator.
   *
   * @param data list of data for all sensors
   * @return a single JsObject which is the average for that sensor
   */
  def binData(data: PeekIterator[JsObject], time: String, depth: Double, keepRaw: Boolean): Option[JsObject] = {
    if (!data.hasNext) return None

    // TODO list of special properties
    val groupBy = List("DEPTH_CODE")
    val addAll = List("source")
    val ignore = groupBy ++ addAll

    // list of result
    val properties = collection.mutable.HashMap.empty[String, collection.mutable.HashMap[String, BinHelper]]

    // loop over all values
    var count = 0
    var timer = System.currentTimeMillis()
    val sensorName = data.peek.get.\("sensor_name")
    do {
      val sensor = data.next

      // get depth code
      val depthCode = sensor.\("properties").\("DEPTH_CODE") match {
        case x: JsUndefined => "NA"
        case x => Parsers.parseString(x)
      }
      val extras = Json.obj("depth_code" -> depthCode)

      // get source
      val source = sensor.\("properties").\("source") match {
        case x: JsUndefined => ""
        case x => Parsers.parseString(x)
      }

      // get depth
      val coordinates = sensor.\("geometry").\("coordinates").as[JsArray]
      val depthBin = depth * Math.ceil(Parsers.parseDouble(coordinates(2)).getOrElse(0.0) / depth)

      // bin time
      val startTime = Parsers.parseDate(sensor.\("start_time")).getOrElse(DateTime.now)
      val endTime = Parsers.parseDate(sensor.\("end_time")).getOrElse(DateTime.now)
      val times = timeBins(time, startTime, endTime)

      // each property is either added to all, or is new result
      sensor.\("properties").as[JsObject].fieldSet.filter(p => !ignore.contains(p._1)).foreach(f => {
        // add to list of properies
        val prop = Parsers.parseString(f._1)
        val propertyBin = properties.getOrElseUpdate(prop, collection.mutable.HashMap.empty[String, BinHelper])

        // add value to all bins
        times.foreach(t => {
          val key = prop + t._1 + depthBin + depthCode

          // add data object
          val bin = propertyBin.getOrElseUpdate(key, BinHelper(depthBin, t._1, extras, t._2))

          // add source to result
          if (source != "") {
            bin.sources += source
          }

          // add values to array
          Parsers.parseDouble(f._2) match {
            case Some(v) => bin.doubles += v
            case None =>
              f._2 match {
                case JsObject(_) => {
                  val s = Parsers.parseString(f._2)
                  if (s != "") {
                    bin.array += s
                  }
                }

                case _ => {
                  val s = Parsers.parseString(f._2)
                  if (s != "") {
                    bin.strings += s
                  }
              }

            }
          }
        })
      })
      count += 1
    } while (data.hasNext() && sensorName.equals(data.peek().get.\("sensor_name")))
    timer = System.currentTimeMillis() - timer
    Logger.debug(s"Took ${timer}ms to bin ${count} values for ${sensorName.toString}")

    // combine results
    val result = properties.map{p =>
      val elements = for(bin <- p._2.values if bin.doubles.length > 0 || bin.array.size > 0) yield {
        val base = Json.obj("depth" -> bin.depth, "label" -> bin.label, "sources" -> bin.sources.toList)

        val raw = if (keepRaw) {
          Json.obj("doubles" -> bin.doubles.toList, "strings" -> bin.strings.toList)
        } else {
          Json.obj()
        }

        val dlen = bin.doubles.length
        val average = if(dlen > 0) {
          Json.obj("average" -> toJson(bin.doubles.sum / dlen), "count" -> dlen)
        } else {
          Json.obj("array" -> bin.array.toList, "count" -> bin.array.size)
        }

        // return object combining all pieces
        base ++ bin.timeInfo ++ bin.extras ++ raw ++ average
      }
      // add data back to result, sorted by date.
      (p._1, elements.toList.sortWith((x, y) => x.\("date").toString() < y.\("date").toString()))
    }

    Some(Json.obj("sensor_name" -> Parsers.parseString(sensorName), "properties" -> Json.toJson(result.toMap)))
  }

  case class BinHelper(depth: Double,
                       label: String,
                       extras: JsObject,
                       timeInfo: JsObject,
                       doubles: collection.mutable.ListBuffer[Double] = collection.mutable.ListBuffer.empty[Double],
                       array: collection.mutable.HashSet[String] = collection.mutable.HashSet.empty[String],
                       strings: collection.mutable.HashSet[String] = collection.mutable.HashSet.empty[String],
                       sources: collection.mutable.HashSet[String] = collection.mutable.HashSet.empty[String])

  def timeBins(time: String, startTime: DateTime, endTime: DateTime): Map[String, JsObject] = {
    val iso = ISODateTimeFormat.dateTime()
    val result = collection.mutable.HashMap.empty[String, JsObject]

    time.toLowerCase match {
      case "decade" => {
        var counter = new DateTime((startTime.getYear / 10) * 10, 1, 1, 0, 0, 0)
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val year = counter.getYear
          val date = new DateTime(year + 5,7,1,12,0,0)
          result.put(year.toString, Json.obj("year" -> year, "date" -> iso.print(date)))
          counter = counter.plusYears(10)
        }
      }
      case "lustrum" => {
        var counter = new DateTime((startTime.getYear / 5) * 5, 1, 1, 0, 0, 0)
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val year = counter.getYear
          val date = new DateTime(year + 2,7,1,12,0,0)
          result.put(year.toString, Json.obj("year" -> year, "date" -> iso.print(date)))
          counter = counter.plusYears(5)
        }
      }
      case "year" => {
        var counter = new DateTime(startTime.getYear, 1, 1, 0, 0, 0)
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val year = counter.getYear
          val date = new DateTime(year,7,1,12,0,0,0)
          result.put(year.toString, Json.obj("year" -> year, "date" -> iso.print(date)))
          counter = counter.plusYears(1)
        }
      }
      case "semi" => {
        var counter = startTime
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val year = counter.getYear
          if (counter.getMonthOfYear < 7) {
            result.put(year + " spring", Json.obj("year" -> year,
              "date" -> iso.print(new DateTime(year, 3, 1, 12, 0, 0))))

          } else {
            result.put(year + " summer", Json.obj("year" -> year,
              "date" -> iso.print(new DateTime(year, 9, 1, 12, 0, 0))))
          }
          counter = counter.plusMonths(6)
        }
      }
      case "season" => {
        var counter = startTime
        while(counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val year = counter.getYear
          if ((counter.getMonthOfYear < 3) || (counter.getMonthOfYear == 3 && counter.getDayOfMonth < 21)) {
            result.put(year + " winter", Json.obj("year" -> year,
              "date" -> iso.print(new DateTime(year, 2, 1, 12, 0, 0))))
          } else if ((counter.getMonthOfYear < 6) || (counter.getMonthOfYear == 6 && counter.getDayOfMonth < 21)) {
            result.put(year + " spring", Json.obj("year" -> year,
              "date" -> iso.print(new DateTime(year, 5, 1, 12, 0, 0))))
          } else if ((counter.getMonthOfYear < 9) || (counter.getMonthOfYear == 9 && counter.getDayOfMonth < 21)) {
            result.put(year + " summer", Json.obj("year" -> year,
              "date" -> iso.print(new DateTime(year, 8, 1, 12, 0, 0))))
          } else if((counter.getMonthOfYear < 12) || (counter.getMonthOfYear == 12 && counter.getDayOfMonth < 21)) {
            result.put(year + " fall", Json.obj("year" -> year,
              "date" -> iso.print(new DateTime(year, 11, 1, 12, 0, 0))))
          } else {
            result.put(year + " winter", Json.obj("year" -> year,
              "date" -> iso.print(new DateTime(year, 2, 1, 12, 0, 0))))
    }
          counter = counter.plusMonths(3)
        }
      }
      case "month" => {
        var counter = startTime
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val label = DateTimeFormat.forPattern("YYYY MMMM").print(counter)
          val year = counter.getYear
          val month = counter.getMonthOfYear
          val date = new DateTime(year,month,15,12,0,0,0)
          result.put(label, Json.obj("year" -> year, "month" -> month, "date" -> iso.print(date)))
          counter = counter.plusMonths(1)
        }
      }
      case "day" => {
        var counter = startTime
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val label = DateTimeFormat.forPattern("YYYY-MM-dd").print(counter)
          val year = counter.getYear
          val month = counter.getMonthOfYear
          val day = counter.getDayOfMonth
          val date = new DateTime(year,month,day,12,0,0,0)
          result.put(label, Json.obj("year" -> year, "month" -> month, "day" -> day, "date" -> iso.print(date)))
          counter = counter.plusDays(1)
        }
      }
      case "hour" => {
        var counter = startTime
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val label = DateTimeFormat.forPattern("YYYY-MM-dd HH").print(counter)
          val year = counter.getYear
          val month = counter.getMonthOfYear
          val day = counter.getDayOfMonth
          val hour = counter.getHourOfDay
          try {
            val date = new DateTime(year,month,day,hour,30,0,0)
            result.put(label, Json.obj("year" -> year, "month" -> month, "day" -> day, "hour" -> hour, "date" -> iso.print(date)))
          } catch {
            case e: IllegalInstantException => Logger.debug("Invalid Instant Exception", e)
          }
          counter = counter.plusHours(1)
        }
      }
      case "minute" => {
        var counter = startTime
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val label = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm").print(counter)
          val year = counter.getYear
          val month = counter.getMonthOfYear
          val day = counter.getDayOfMonth
          val hour = counter.getHourOfDay
          val minute = counter.getMinuteOfHour
          try {
            val date = new DateTime(year,month,day,hour,minute,30,0)
            result.put(label, Json.obj("year" -> year, "month" -> month, "day" -> day, "hour" -> hour, "minute" -> minute, "date" -> iso.print(date)))
          } catch {
            case e: IllegalInstantException => Logger.debug("Invalid Instant Exception", e)
          }
          counter = counter.plusMinutes(1)

        }
      }
      case _ => // do nothing
    }

    result.toMap
  }

  def searchDatapoints(operator: String, since: Option[String], until: Option[String], geocode: Option[String], stream_id: Option[String], sensor_id: Option[String],
                       sources: List[String], attributes: List[String], format: String, semi: Option[String], onlyCount: Boolean,
                       window_start: Option[String] = None, window_end: Option[String] = None, binning: String, geojson: Option[String]) =
    PermissionAction(Permission.ViewGeoStream) { implicit request =>
      current.plugin[PostgresPlugin] match {
        case Some(plugin) if plugin.isEnabled => {
          val description = Json.obj("format" -> format,
            "operator" -> operator,
            "since" -> since.getOrElse("").toString,
            "until" -> until.getOrElse("").toString,
            "geocode" -> geocode.getOrElse("").toString,
            "stream_id" -> stream_id.getOrElse("").toString,
            "sensor_id" -> sensor_id.getOrElse("").toString,
            "sources" -> Json.toJson(sources),
            "attributes" -> Json.toJson(attributes),
            "semi" -> semi.getOrElse("").toString)
          cacheFetch(description) match {
            case Some(data) => {
              if (format == "csv") {
                Ok.chunked(data &> Gzip.gzip())
                  .withHeaders(("Content-Disposition", "attachment; filename=datapoints.csv"),
                               ("Content-Encoding", "gzip"))
                  .as(withCharset("text/csv"))
              } else {
                jsonp(data.through(Enumeratee.map(new String(_))), request)
              }
            }
            case None => {
              // if computing trends need all data
              val raw = geojson match {
                case Some(gj) => {
                  plugin.searchDatapointsGeoJson (since, until, geojson, stream_id, sensor_id, sources, attributes, operator != "")
                }
                case None => {
                  plugin.searchDatapoints (since, until, geocode, stream_id, sensor_id, sources, attributes, operator != "")
                }
              }
              val filtered = raw.filter(p => filterDataBySemi(p, semi))
              // TODO fix this for better grouping see MMDB-1678
              val data = calculate(operator, filtered, window_start, window_end, semi.isDefined, binning)

              if(onlyCount) {
                cacheWrite(description, formatResult(data, format))
                Ok(toJson(Map("datapointsLength" -> data.length)))
              } else if (format == "csv") {
                val toByteArray: Enumeratee[String, Array[Byte]] = Enumeratee.map[String]{ s => s.getBytes }
                Ok.chunked(cacheWrite(description, jsonToCSV(data)) &> toByteArray  &> Gzip.gzip())
                  .withHeaders(("Content-Disposition", "attachment; filename=datapoints.csv"),
                               ("Content-Encoding", "gzip"))
                  .as(withCharset("text/csv"))
              } else {
                jsonp(cacheWrite(description, formatResult(data, format)), request)
              }

            }
          }
        }
        case _ => pluginNotEnabled
      }
    }

  def getDatapoint(id: String) =  PermissionAction(Permission.ViewGeoStream) { implicit request =>
    Logger.debug("Get datapoint " + id)
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        plugin.getDatapoint(id) match {
          case Some(d) => jsonp(d, request)
          case None => jsonp("""{"status":"No stream found"}""", request)
        }
      }
      case _ => pluginNotEnabled
    }
  }


  def formatResult(data :Iterator[JsObject], format: String) = {
    var status = 0
    Enumerator.generateM(Future[Option[String]] {
      status match {
        case 0 => {
          status = 1
          if (format == "geojson") {
            Some("{ \"type\": \"FeatureCollection\", \"features\": [")
          } else {
            Some("[")
          }
        }
        case 1 => {
          if (data.hasNext) {
            val v = data.next().toString()
            if (data.hasNext) {
              Some(v + ",\n")
            } else {
              Some(v + "\n")
            }
          } else {
            status = 2
            if (format == "geojson") {
              Some("] }")
            } else {
              Some("]")
            }
          }
        }
        case 2 => {
          None
        }
      }
    })
  }

  def filterDataBySemi(obj: JsObject, semi: Option[String]): Boolean = {
    if (semi.isEmpty) return true

    // semi only needed if spring or summer
    if (semi.get.toLowerCase != "spring" && semi.get.toLowerCase != "summer") return true

    // get start/end
    val startTime = Parsers.parseDate(obj.\("start_time")) match {
      case Some(x) => x
      case None => return false
    }
    val endTime = Parsers.parseDate(obj.\("end_time")) match {
      case Some(x) => x
      case None => return false
    }

    // see if start end dates are a year apart, if so it is ok
    if (startTime.getYear != endTime.getYear) return true

    // otherwise see if start is before Jul 1st in case of spring
    if (semi.get.toLowerCase == "spring" && startTime.getMonthOfYear < 7) return true

    // otherwise see if end is after Jul 1st in case of summer
    if (semi.get.toLowerCase == "summer" && endTime.getMonthOfYear > 6) return true

    // wrong time
    false
  }

  // ----------------------------------------------------------------------
  // Calculations
  // ----------------------------------------------------------------------

  def calculate(operator: String, data: Iterator[JsObject], window_start: Option[String], window_end: Option[String], semiGroup: Boolean, binning: String): Iterator[JsObject] = {
    if (operator == "") return data

    val peekIter = new PeekIterator(data)
    val trendStart = if (window_start.isDefined) {
      DateTime.parse(window_start.get.replace(" ", "T"))
    } else {
      DateTime.now.minusYears(10)
    }
    val trendEnd = if (window_end.isDefined) {
      DateTime.parse(window_end.get.replace(" ", "T"))
    } else {
      DateTime.now
    }

    new Iterator[JsObject] {
      var nextObject: Option[JsObject] = None

      def hasNext = {
        if (nextObject.isDefined) {
          true
        } else {
          try {
            nextObject = operator.toLowerCase match {
              case "averages" => computeAverage(peekIter)
              case "trends" => computeTrends(peekIter, trendStart, trendEnd, semiGroup, binning)
              case _ => None
            }
          } catch {
            case t:Throwable => {
               nextObject = None
              Logger.error("Error computing next value.", t)
            }
          }
          nextObject.isDefined
        }
      }

      def next() = {
        if (hasNext) {
          val x = nextObject.get
          nextObject = None
          x
        } else {
          null
        }
      }
    }
  }

  /**
   * Compute the average value for a sensor. This will return a single
   * sensor with the average values for that sensor. The next object is
   * waiting in the peekIterator.
   *
   * @param data list of data for all sensors
   * @return a single JsObject which is the average for that sensor
   */
  def computeAverage(data: PeekIterator[JsObject]): Option[JsObject] = {
    if (!data.hasNext) return None

    val sensor = data.next()
    val counter = collection.mutable.HashMap.empty[String, Int]
    val properties = collection.mutable.HashMap.empty[String, Either[collection.mutable.ListBuffer[String], Double]]
    var startDate = Parsers.parseString(sensor.\("start_time"))
    var endDate = Parsers.parseString(sensor.\("end_time"))
    var streams = collection.mutable.ListBuffer[String](Parsers.parseString(sensor.\("stream_id")))
    sensor.\("properties").as[JsObject].fieldSet.foreach(f => {
      counter(f._1) = 1
      val s = Parsers.parseString(f._2)
      val v = Parsers.parseDouble(s)
      if (v.isDefined) {
        properties(f._1) = Right(v.get)
      } else {
        properties(f._1) = Left(collection.mutable.ListBuffer[String](s))
      }
    })
    val sensorName = sensor.\("sensor_name")

    while (data.hasNext && sensorName.equals(data.peek().get.\("sensor_name"))) {
      val nextSensor = data.next()
      if (startDate.compareTo(Parsers.parseString(nextSensor.\("start_time"))) > 0) {
        startDate = Parsers.parseString(nextSensor.\("start_time"))
      }
      if (endDate.compareTo(Parsers.parseString(nextSensor.\("end_time").toString())) < 0) {
        endDate = Parsers.parseString(nextSensor.\("end_time"))
      }
      if (!streams.contains(Parsers.parseString(nextSensor.\("stream_id")))) {
        streams += Parsers.parseString(nextSensor.\("stream_id"))
      }
      nextSensor.\("properties").as[JsObject].fieldSet.foreach(f => {
        if (properties contains f._1) {
          properties(f._1) match {
            case Left(l) => {
              val s = Parsers.parseString(f._2)
              if (counter(f._1) == 1) {
                val v = Parsers.parseDouble(s)
                if (v.isDefined) {
                  properties(f._1) = Right(v.get)
                }
              } else {
                if (!l.contains(s)) {
                  counter(f._1) = counter(f._1) + 1
                  l += s
                }
              }
            }
            case Right(d) => {
              val v2 = Parsers.parseDouble(f._2)
              if (v2.isDefined) {
                counter(f._1) = counter(f._1) + 1
                properties(f._1) = Right(d + v2.get)
              }
            }
          }
        } else {
          val s = Parsers.parseString(f._2)
          val v = Parsers.parseDouble(s)
          counter(f._1) = 1
          if (v.isDefined) {
            properties(f._1) = Right(v.get)
          } else {
            properties(f._1) = Left(collection.mutable.ListBuffer[String](s))
          }
        }
      })
    }

    // compute average
    val jsProperties = collection.mutable.HashMap.empty[String, JsValue]
    properties.foreach(f => {
      jsProperties(f._1) = properties(f._1) match {
        case Left(l) => {
          if (counter(f._1) == 1) {
            Json.toJson(l.head)
          } else {
            Json.toJson(l.toArray)
          }
        }
        case Right(d) => Json.toJson(d / counter(f._1))
      }
    })

    // update sensor
    Some(sensor ++ Json.obj("properties" -> Json.toJson(jsProperties.toMap),
      "start_time" -> startDate,
      "end_time"   -> endDate,
      "stream_id"  -> Json.toJson(streams)))
  }

  /**
   * Compute the average value for each sensor. This will an object per sensor
   * that contains the average data for a point, as well as an array of
   * values for all data that is not a number.
   *
   * @param data list of data for all sensors
   * @return an array with a all sensors and the average values.
   */
  def computeTrends(data: PeekIterator[JsObject], since: DateTime, until: DateTime, semiGroup: Boolean, binning: String): Option[JsObject] = {
    if (!data.hasNext) return None

    val counterTrend = collection.mutable.HashMap.empty[String, Int]
    val counterAll = collection.mutable.HashMap.empty[String, Int]
    val counterLast = collection.mutable.HashMap.empty[String, Int]
    val propertiesTrend = collection.mutable.HashMap.empty[String, Either[collection.mutable.ListBuffer[String], Double]]
    val propertiesAll = collection.mutable.HashMap.empty[String, Either[collection.mutable.ListBuffer[String], Double]]
    val propertiesLast = collection.mutable.HashMap.empty[String, Either[collection.mutable.ListBuffer[String], Double]]
    val sensor = data.next()
    var startDate = Parsers.parseString(sensor.\("start_time"))
    var endDate = Parsers.parseString(sensor.\("end_time"))
    var streams = collection.mutable.ListBuffer[String](Parsers.parseString(sensor.\("stream_id")))
    sensor.\("properties").as[JsObject].fieldSet.foreach(f => {
      counterTrend(f._1) = 1
      counterAll(f._1) = 1
      val s = Parsers.parseString(f._2)
      val v = Parsers.parseDouble(s)
      if (v.isDefined) {
        propertiesTrend(f._1) = Right(v.get)
        propertiesAll(f._1) = Right(v.get)
      } else {
        propertiesTrend(f._1) = Left(collection.mutable.ListBuffer[String](s))
        propertiesAll(f._1) = Left(collection.mutable.ListBuffer[String](s))
      }
    })
    val sensorName = sensor.\("sensor_name")
    var lastBin = "nada"
    while (data.hasNext && sensorName.equals(data.peek().get.\("sensor_name"))) {
      val nextSensor = data.next()
      val sensorStart = Parsers.parseString(nextSensor.\("start_time"))
      val sensorEnd = Parsers.parseString(nextSensor.\("end_time"))
      if (startDate.compareTo(sensorStart) > 0) {
        startDate = sensorStart
      }
      if (endDate.compareTo(sensorEnd) < 0) {
        endDate = sensorEnd
      }
      // check to see what bin this is
      // TODO fix this for better grouping see MMDB-1678
      val currentBin = timeBins(binning, Parsers.parseDate(sensorStart).get, Parsers.parseDate(sensorEnd).get).keys.last
      if (!streams.contains(Parsers.parseString(nextSensor.\("stream_id")))) {
        streams += Parsers.parseString(nextSensor.\("stream_id"))
      }
      nextSensor.\("properties").as[JsObject].fieldSet.foreach(f => {
        // compute last grouping worth of data
        // TODO fix this for better grouping see MMDB-1678
        if (semiGroup) {
          if (lastBin != currentBin) {
            counterLast.clear()
            propertiesLast.clear()
            lastBin = currentBin
          }
          if (propertiesLast contains f._1) {
            propertiesLast(f._1) match {
              case Left(l) => {
                val s = Parsers.parseString(f._2)
                if (counterLast(f._1) == 1) {
                  val v = Parsers.parseDouble(s)
                  if (v.isDefined) {
                    propertiesLast(f._1) = Right(v.get)
                  }
                } else {
                  if (!l.contains(s)) {
                    counterLast(f._1) = counterLast(f._1) + 1
                    l += s
                  }
                }
              }
              case Right(d) => {
                val v2 = Parsers.parseDouble(f._2)
                if (v2.isDefined) {
                  counterLast(f._1) = counterLast(f._1) + 1
                  propertiesLast(f._1) = Right(d + v2.get)
                }
              }
            }
          } else {
            val s = Parsers.parseString(f._2)
            val v = Parsers.parseDouble(s)
            counterLast(f._1) = 1
            if (v.isDefined) {
              propertiesLast(f._1) = Right(v.get)
            } else {
              propertiesLast(f._1) = Left(collection.mutable.ListBuffer[String](s))
            }
          }
        }

        if (propertiesAll contains f._1) {
          propertiesAll(f._1) match {
            case Left(l) => {
              val s = Parsers.parseString(f._2)
              if (counterAll(f._1) == 1) {
                val v = Parsers.parseDouble(s)
                if (v.isDefined) {
                  propertiesAll(f._1) = Right(v.get)
                }
              } else {
                if (!l.contains(s)) {
                  counterAll(f._1) = counterAll(f._1) + 1
                  l += s
                }
              }
            }
            case Right(d) => {
              val v2 = Parsers.parseDouble(f._2)
              if (v2.isDefined) {
                counterAll(f._1) = counterAll(f._1) + 1
                propertiesAll(f._1) = Right(d + v2.get)
              }
            }
          }
        } else {
          val s = Parsers.parseString(f._2)
          val v = Parsers.parseDouble(s)
          counterAll(f._1) = 1
          if (v.isDefined) {
            propertiesAll(f._1) = Right(v.get)
          } else {
            propertiesAll(f._1) = Left(collection.mutable.ListBuffer[String](s))
          }
        }
      })
      if ((since.compareTo(DateTime.parse(sensorEnd.replace(" ", "T"))) <= 0) && (until.compareTo(DateTime.parse(sensorStart.replace(" ", "T"))) >= 0)) {
        nextSensor.\("properties").as[JsObject].fieldSet.foreach(f => {
          if (propertiesTrend contains f._1) {
            propertiesTrend(f._1) match {
              case Left(l) => {
                val s = Parsers.parseString(f._2)
                if (counterTrend(f._1) == 1) {
                  val v = Parsers.parseDouble(s)
                  if (v.isDefined) {
                    propertiesTrend(f._1) = Right(v.get)
                  }
                } else {
                  if (!l.contains(s)) {
                    counterTrend(f._1) = counterTrend(f._1) + 1
                    l += s
                  }
                }
              }
              case Right(d) => {
                val v2 = Parsers.parseDouble(f._2)
                if (v2.isDefined) {
                  counterTrend(f._1) = counterTrend(f._1) + 1
                  propertiesTrend(f._1) = Right(d + v2.get)
                }
              }
            }
          } else {
            val s = Parsers.parseString(f._2)
            val v = Parsers.parseDouble(s)
            counterTrend(f._1) = 1
            if (v.isDefined) {
              propertiesTrend(f._1) = Right(v.get)
            } else {
              propertiesTrend(f._1) = Left(collection.mutable.ListBuffer[String](s))
            }
          }
        })
      }
    }

    // compute trend
    // (( Average of time - Average of life) / Average over life) * 100
    val jsProperties = collection.mutable.HashMap.empty[String, JsValue]
    propertiesTrend.foreach(f => {
      jsProperties(f._1) = propertiesTrend(f._1) match {
        case Left(l) => {
          if (counterTrend(f._1) == 1) {
            Json.toJson(l.head)
          } else {
            Json.toJson(l.toArray)
          }
        }
        case Right(d) => {
          if (propertiesAll(f._1).isRight) {
            val avgAll = propertiesAll(f._1).right.get / counterAll(f._1)
            val avgTrend = d / counterTrend(f._1)
            jsProperties(f._1 + "_total_average") = Json.toJson(avgAll)
            jsProperties(f._1 + "_interval_average") =  Json.toJson(avgTrend)
            // TODO fix this for better grouping see MMDB-1678
            if (semiGroup) {
              if (propertiesLast.contains(f._1) && propertiesLast(f._1).isRight) {
                val avgLast = propertiesLast(f._1).right.get / counterLast(f._1)
                jsProperties(f._1 + "_last_average") = Json.toJson(avgLast)
              } else {
                Logger.debug("Error getting last value, non number as last value.")
              }
            }
            jsProperties(f._1 + "_percentage_change") = Json.toJson(((avgTrend - avgAll) / avgAll) * 100.0)
            null
          } else {
            Json.toJson("no data")
          }
        }
      }
    })

    // update sensor
    if (semiGroup) {
      Some(sensor ++ Json.obj("properties" -> Json.toJson(jsProperties.filter(p => p._2 != null).toMap),
        "start_time" -> startDate,
        "end_time"   -> endDate,
        "last_time" -> lastBin,
        "stream_id"  -> Json.toJson(streams)))
    } else {
      Some(sensor ++ Json.obj("properties" -> Json.toJson(jsProperties.filter(p => p._2 != null).toMap),
        "start_time" -> startDate,
        "end_time"   -> endDate,
        "stream_id"  -> Json.toJson(streams)))
    }
  }

  /**
   * Compute the average values over an area. This will return a single object
   * that contains the average data for a point, as well as an array of
   * values for all data that is not a number.
   *
   * @param data list of sensors inside the area
   * @param geocode the actual area definition
   * @return an array with a single sensor that has the average values.
   */
  def computeAverageArea(data: List[JsObject], geocode: Option[String]) = {
    val coordinates = geocode match {
      case Some(x) => {
        val points = x.split(",")
        val coordinates = collection.mutable.ListBuffer.empty[JsValue]
        var index = 0
        while (index < points.length - 1) {
          val lat = Parsers.parseDouble(points(index))
          val lon = Parsers.parseDouble(points(index+1))
          if (lat.isDefined && lon.isDefined) {
            coordinates += Json.toJson(Array(lon.get, lat.get))
          }
          index += 2
        }
        Json.toJson(coordinates.toArray)
      }
      case None => Json.toJson(Array.empty[Double])
    }
    val geometry = Map("type" -> Json.toJson("Polygon"), "coordinates" -> Json.toJson(Array(coordinates)))

    var startDate = ""
    var endDate = ""
    val counter = collection.mutable.HashMap.empty[String, Int]
    val properties = collection.mutable.HashMap.empty[String, Either[collection.mutable.ListBuffer[String], Double]]
    var streams = collection.mutable.ListBuffer.empty[String]
    var sensors = collection.mutable.ListBuffer.empty[String]
    data.foreach(sensor => {
      sensor.\("properties").as[JsObject].fieldSet.foreach(f => {
        if (startDate.isEmpty || startDate.compareTo(Parsers.parseString(sensor.\("start_time"))) > 0) {
          startDate = Parsers.parseString(sensor.\("start_time"))
        }
        if (endDate.isEmpty || endDate.compareTo(Parsers.parseString(sensor.\("end_time"))) < 0) {
          endDate = Parsers.parseString(sensor.\("end_time"))
        }
        if (!sensors.contains(Parsers.parseString(sensor.\("sensor_id")))) {
          sensors += Parsers.parseString(sensor.\("sensor_id"))
        }
        if (!streams.contains(Parsers.parseString(sensor.\("stream_id")))) {
          streams += Parsers.parseString(sensor.\("stream_id"))
        }
        if (properties contains f._1) {
          properties(f._1) match {
            case Left(l) => {
              val s = Parsers.parseString(f._2)
              if (counter(f._1) == 1) {
                val v = Parsers.parseDouble(s)
                if (v.isDefined) {
                  properties(f._1) = Right(v.get)
                }
              } else {
                if (!l.contains(s)) {
                  counter(f._1) = counter(f._1) + 1
                  l += s
                }
              }
            }
            case Right(d) => {
              val v = Parsers.parseDouble(f._2)
              if (v.isDefined) {
                counter(f._1) = counter(f._1) + 1
                properties(f._1) = Right(d + v.get)
              }
            }
          }
        } else {
          counter(f._1) = 1
          val s = Parsers.parseString(f._2)
          val v = Parsers.parseDouble(s)
          if (v.isDefined) {
            properties(f._1) = Right(v.get)
          } else {
            properties(f._1) = Left(collection.mutable.ListBuffer[String](s))
          }
        }
      })
    })

    // compute average
    val jsProperties = collection.mutable.HashMap.empty[String, JsValue]
    properties.foreach(f => {
      jsProperties(f._1) = f._2 match {
        case Left(l) => {
          if (counter(f._1) == 1) {
            Json.toJson(l.head)
          } else {
            Json.toJson(l.toArray)
          }
        }
        case Right(d) => Json.toJson(d / counter(f._1))
      }
    })

    val sensor = Json.obj("id"          -> Json.toJson(-1),
                          "start_time"  -> Json.toJson(startDate),
                          "end_time"    -> Json.toJson(endDate),
                          "type"        -> Json.toJson("Feature"),
                          "geometry"    -> Json.toJson(geometry),
                          "sensor_id"   -> Json.toJson(sensors.toArray),
                          "stream_id"   -> Json.toJson(streams.toArray),
                          "sensor_name" -> Json.toJson("area"),
                          "properties"  -> Json.toJson(jsProperties.toMap))

    List[JsObject](sensor)
  }

  /**
   * Compute the number of times a specific property is found in the data.
   *
   * @param data list of sensors found
   * @return list of sensors where properties show the number of times a
   *         specific property has been seen for that sensor.
   */
  def computeCountSensor(data: List[JsObject]) = {
    var rowCount = 0
    val result = collection.mutable.ListBuffer.empty[JsObject]

    while (rowCount < data.length) {
      val counter = collection.mutable.HashMap.empty[String, Int]
      val sensor = data(rowCount)
      var startDate = Parsers.parseString(sensor.\("start_time"))
      var endDate = Parsers.parseString(sensor.\("end_time"))
      var streams = collection.mutable.ListBuffer[String](Parsers.parseString(sensor.\("stream_id")))
      sensor.\("properties").as[JsObject].fieldSet.foreach(f => {
        counter(f._1) = 1
      })
      while (rowCount < data.length && sensor.\("sensor_name").equals(data(rowCount).\("sensor_name"))) {
        val nextSensor = data(rowCount)
        if (startDate.compareTo(Parsers.parseString(nextSensor.\("start_time"))) > 0) {
          startDate = Parsers.parseString(nextSensor.\("start_time"))
        }
        if (endDate.compareTo(Parsers.parseString(nextSensor.\("end_time"))) < 0) {
          endDate = Parsers.parseString(nextSensor.\("end_time"))
        }
        if (!streams.contains(Parsers.parseString(nextSensor.\("stream_id")))) {
          streams += Parsers.parseString(nextSensor.\("stream_id"))
        }
        nextSensor.\("properties").as[JsObject].fieldSet.foreach(f => {
          if (counter contains f._1) {
            counter(f._1) = counter(f._1) + 1
          } else {
            counter(f._1) = 1
          }
        })
        rowCount += 1
      }

      // compute average
      val jsProperties = collection.mutable.HashMap.empty[String, JsValue]
      counter.foreach(f => {
        jsProperties(f._1) = Json.toJson(counter(f._1))
      })

      // update sensor
      result += (sensor ++ Json.obj("properties" -> Json.toJson(jsProperties.toMap),
                                    "start_time" -> startDate,
                                    "end_time"   -> endDate,
                                    "stream_id"  -> Json.toJson(streams)))
    }

    result.toList
  }

  /**
   * Returns the request potentially as jsonp
    *
    * @param data Result to transform
   * @param request request made to server
   */
  def jsonp(data:String, request: Request[Any]): SimpleResult = {
    jsonp(Enumerator(data), request)
  }

  /**
   * Returns the request potentially as jsonp
    *
    * @param data Result to transform
   * @param request request made to server
   */
  def jsonp(data:JsValue, request: Request[Any]): SimpleResult = {
    jsonp(Enumerator(data.toString), request)
  }

  /**
   * Tries to transform a response into a JavaScript expression.
    *
    * @param data Result to transform
   * @param request request made to server
   */
  def jsonp(data:Enumerator[String], request: Request[Any]) = {
    val toByteArray: Enumeratee[String, Array[Byte]] = Enumeratee.map[String]{ s => s.getBytes }
    request.getQueryString("callback") match {
      case Some(callback) => Ok.chunked(Enumerator(s"$callback(") >>> data >>> Enumerator(");") &> toByteArray &> Gzip.gzip())
        .withHeaders(("Content-Encoding", "gzip"))
        .as(JAVASCRIPT)
      case None => Ok.chunked(data &> toByteArray &> Gzip.gzip())
        .withHeaders(("Content-Encoding", "gzip"))
        .as(JSON)
    }
  }

  // ----------------------------------------------------------------------
  // CACHE RESULTS
  // ----------------------------------------------------------------------

  /**
   * Write the file to cache, filename in cache is based on MD5(description).
   * A second file with the same name but with extension .json is saved that
   * will hold teh actual description.
   *
   * @param description json object describing the contents of the file.
   * @param data the actual data that should be saved
   * @return an enumerator that will save data as data is being enumerated.
   */
  def cacheWrite(description: JsObject, data:Enumerator[String]): Enumerator[String] = {
    play.api.Play.configuration.getString("geostream.cache") match {
      case Some(x) => {
        val cacheFolder = new File(x)
        if (cacheFolder.isDirectory || cacheFolder.mkdirs) {
          val filename = MessageDigest.getInstance("MD5").digest(description.toString().getBytes).map("%02X".format(_)).mkString
          new PrintStream(new File(cacheFolder, filename + ".json")).print(description.toString())
          val writer = new PrintStream(new File(cacheFolder, filename))
          val save: Enumeratee[String, String] = Enumeratee.map { s =>
            writer.print(s)
            s
          }
          data.through(save)
        } else {
          data
        }
      }
      case None => data
    }
  }

  /**
   * Return a list of all files and their descriptions in the cache.
   */
  def cacheListAction() = PermissionAction(Permission.ViewGeoStream) { implicit request =>
    play.api.Play.configuration.getString("geostream.cache") match {
      case Some(x) => {
        val files = collection.mutable.Map.empty[String, JsValue]
        var total = 0l
        for (file <- new File(x).listFiles) {
          val jsonFile = new File(file.getAbsolutePath + ".json")
          if (jsonFile.exists()) {
            val data = Json.parse(Source.fromFile(jsonFile).mkString)
            files.put(file.getName, data.as[JsObject] ++ Json.obj("filesize" -> file.length,
              "created" -> ISODateTimeFormat.dateTime.print(new DateTime(jsonFile.lastModified))))
          }
          total += file.length()
        }
        Ok(Json.obj("files" -> Json.toJson(files.toMap),
                    "size" -> total))
      }
      case None => {
        NotFound("Cache is not enabled")
      }
    }
  }

  /**
   * Checks to see if a file with the MD5(description) exists, if so this
   * will return a Enumerator of that file, otherwise it will return None.
   */
  def cacheFetch(description: JsObject) = {
    play.api.Play.configuration.getString("geostream.cache") match {
      case Some(x) => {
        val filename = MessageDigest.getInstance("MD5").digest(description.toString().getBytes).map("%02X".format(_)).mkString
        val cacheFile = new File(x, filename)
        if (cacheFile.exists)
          Some(Enumerator.fromFile(cacheFile))
        else
          None
      }
      case None => None
    }
  }

  /**
   * Return the file with the given name.
   */
  def cacheFetchAction(filename: String) =  PermissionAction(Permission.ViewGeoStream) { implicit request =>
    play.api.Play.configuration.getString("geostream.cache") match {
      case Some(x) => {
        val file = new File(x, filename)
        if (file.exists) {
          val data = Json.parse(Source.fromFile(new File(x, filename + ".json")).mkString)
          Parsers.parseString(data.\("format")) match {
            case "csv" => Ok.chunked(Enumerator.fromFile(file) &> Gzip.gzip()).as(withCharset("text/csv"))
            case "json" => Ok.chunked(Enumerator.fromFile(file) &> Gzip.gzip()).as(JSON)
            case "geojson" => Ok.chunked(Enumerator.fromFile(file) &> Gzip.gzip()).as(JSON)
            case _ => Ok.chunked(Enumerator.fromFile(file) &> Gzip.gzip()).as(TEXT)
          }
        } else {
          NotFound("File not found in cache")
        }
      }
      case None => {
        NotFound("Cache is not enabled")
      }
    }
  }

  /**
   * Remove the file with the MD5(description) from the cache as well as
   * the associated json file.
   */
  def cacheInvalidate(description: JsObject) {
    play.api.Play.configuration.getString("geostream.cache") match {
      case Some(x) => {
        val filename = MessageDigest.getInstance("MD5").digest(description.toString().getBytes).map("%02X".format(_)).mkString
        val cacheFile = new File(x, filename)
        if (cacheFile.exists)
          cacheFile.delete
        val cacheFileJson = new File(x, filename + ".json")
        if (cacheFileJson.exists)
          cacheFileJson.delete
      }
      case None => // do nothing
    }
  }

  /**
   * Removes all files from the cache,
   * or the files associated with either sensor_id or stream_id as query parameters.
   * Specifying sensor_id and stream_id will only remove caching where both are present
   */
  def cacheInvalidate(sensor_id: Option[String] = None, stream_id: Option[String] = None) = {
    play.api.Play.configuration.getString("geostream.cache") match {
      case Some(x) => {
        val existingFiles = new File(x).listFiles
        val filesToRemove = collection.mutable.ListBuffer.empty[String]
        val streams = new ListBuffer[String]()
        val sensors = new ListBuffer[String]()
        val errors = new ListBuffer[String]()

        if (sensor_id.isDefined) {
          sensors += sensor_id.get.toString
          current.plugin[PostgresPlugin] match {
            case Some(plugin) if plugin.isEnabled => {
              plugin.getSensorStreams(sensor_id.get.toString) match {
                case Some(d) => {
                  val responseJson : JsValue = Json.parse(d)
                  (responseJson \\ "stream_id").foreach(streams += _.toString)
                }
                case None => errors += "Sensor " + sensor_id.get.toString + " does not exist"
              }
            }
            case _ => pluginNotEnabled
          }
        }

        if (stream_id.isDefined) {
          streams += stream_id.get.toString
          current.plugin[PostgresPlugin] match {
            case Some(plugin) if plugin.isEnabled => {
              plugin.getStream(stream_id.get.toString) match {
                case Some(d) => {
                  val responseJson : JsValue = Json.parse(d)
                  (responseJson \\ "sensor_id").foreach(sensors += _.asInstanceOf[JsString].value.toString)
                }
                case None => errors += "Stream " + stream_id.get.toString + " does not exist"
              }
            }
            case _ => pluginNotEnabled
          }
        }

        for (file <- existingFiles) {
          val jsonFile = new File(file.getAbsolutePath + ".json")
          if (jsonFile.exists()) {
            val data = Json.parse(Source.fromFile(jsonFile).mkString)
            (Parsers.parseString(data.\("sensor_id")), Parsers.parseString(data.\("stream_id"))) match {
              case (sensor_value, stream_value) =>
                if (sensor_id.isDefined && !stream_id.isDefined) {
                  if (sensors.contains(sensor_value) || streams.contains(stream_value)) {
                    filesToRemove += file.getAbsolutePath
                    filesToRemove += jsonFile.getAbsolutePath
                  }
                }
                if (!sensor_id.isDefined && stream_id.isDefined) {
                  if (streams.contains(stream_value) || (sensors.contains(sensor_value) && stream_value.isEmpty)) {
                    filesToRemove += file.getAbsolutePath
                    filesToRemove += jsonFile.getAbsolutePath
                  }
                }
              case _ => None
            }
          }
        }

        if (sensor_id.isEmpty && stream_id.isEmpty) {
          for (f <- existingFiles) {
            filesToRemove += f.getAbsolutePath
          }
        }
        for (f <- filesToRemove) {
          val file = new File(f)
          if (!file.delete) {
            errors += "Could not delete cache file: " + file.getAbsolutePath
            Logger.error("Could not delete cache file " + file.getAbsolutePath)
          }
        }
        (filesToRemove.toArray, errors.toArray)
      }
      case None => (Array.empty[String], Array.empty[String])
    }
  }

  /**
   * Removes all files from the cache
   */
  def cacheInvalidateAction(sensor_id: Option[String] = None, stream_id: Option[String] = None) =  PermissionAction(Permission.DeleteGeoStream) { implicit request =>
    play.api.Play.configuration.getString("geostream.cache") match {
      case Some(x) => {
        val (files, errors) = cacheInvalidate(sensor_id, stream_id)
        Ok(Json.obj("files" -> Json.toJson(files), "errors" -> Json.toJson(errors)))
      }
      case None => {
        NotFound("Cache is not enabled")
      }
    }
  }

  // ----------------------------------------------------------------------
  // JSON -> CSV
  // ----------------------------------------------------------------------

  /**
   * Returns the JSON formatted as CSV.
   *
   * This will take the first json element and create a header from this. All
   * additional rows will be parsed based on this first header. Any fields not
   * in the first row, will not be outputed.
    *
    * @param data the json to be converted to CSV.
   * @return Enumarator[String]
   */
  def jsonToCSV(data: Iterator[JsObject]): Enumerator[String] = {
    val headers = ListBuffer.empty[Header]

    val configuration = play.api.Play.configuration
    val hidePrefix = configuration.getBoolean("json2csv.hideprefix").getOrElse(false)
    val ignore = configuration.getString("json2csv.ignore").getOrElse("").split(",")
    val prefixSeperator = configuration.getString("json2csv.seperator").getOrElse(" -> ")
    val fixGeometry = configuration.getBoolean("json2csv.fixgeometry").getOrElse(true)

    // load all values, we need to iterate over this list twice, once for headers, once for the data
    // create a new enumerator to return strings chunked.
    var rowcount = 0
    val dataList = data.toList
    Enumerator .generateM(Future[Option[String]] {
      if (headers.isEmpty) {
        // find all headers first
        for (row <- dataList)
          addHeaders(row, headers, ignore, "", prefixSeperator, hidePrefix, fixGeometry)
        Some(printHeader(headers).substring(1) + "\n")
      } else if (rowcount < dataList.length) {
        // return data
        val x = Some(printRow(dataList(rowcount), headers, "", prefixSeperator) + "\n")
        rowcount += 1
        x
      } else {
        None
      }
    })
  }

  /**
   * Helper function to create a new prefix based on the key, and current prefix
   */
  def getPrefix(key: Any, prefix: String, prefixSeperator: String) = {
    if (prefix == "")
       key.toString
     else
       prefix + prefixSeperator + key.toString
  }

  /**
   * Helper function to recursively print the header
   */
  def printHeader(headers: ListBuffer[Header]):String = {
    var result = ""
    for(h <- headers) {
      h.value match {
        case Left(x) => result =result + ",\"" + x + "\""
        case Right(x) => result = result + printHeader(x)
      }
    }
    result
  }

  /**
   * Helper function to create list of headers
   */
  def addHeaders(row: JsObject, headers: ListBuffer[Header], ignore: Array[String], prefix: String, prefixSeperator: String, hidePrefix: Boolean, fixGeometry: Boolean) {
    for(f <- row.fields if !(ignore contains getPrefix(f._1, prefix, prefixSeperator))) {
      f._2 match {
        case y: JsArray => {
          headers.find(x => x.key.equals(f._1)) match {
            case Some(Header(f._1, Left(x))) => Logger.error("Duplicate key [" + f._1 + "] detected")
            case Some(Header(f._1, Right(x))) => addHeaders(y, x, ignore, getPrefix(f._1, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            case Some(x) => Logger.error("Unknown header found : " + x)
            case None => {
              val x = ListBuffer.empty[Header]
              headers += Header(f._1, Right(x))
              addHeaders(y, x, ignore, getPrefix(f._1, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            }
          }
        }
        case y: JsObject => {
          headers.find(x => x.key.equals(f._1)) match {
            case Some(Header(f._1, Left(x))) => Logger.error("Duplicate key [" + f._1 + "] detected")
            case Some(Header(f._1, Right(x))) => addHeaders(y, x, ignore, getPrefix(f._1, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            case Some(x) => Logger.error("Unknown header found : " + x)
            case None => {
              val x = ListBuffer.empty[Header]
              headers += Header(f._1, Right(x))
              addHeaders(y, x, ignore, getPrefix(f._1, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            }
          }
        }
        case y => {
          headers.find(x => x.key.equals(f._1)) match {
            case Some(Header(f._1, Left(x))) => None
            case Some(Header(f._1, Right(x))) => Logger.error("Duplicate key [" + f._1 + "] detected")
            case _ => headers += Header(f._1, Left(getHeader(f._1, prefix, prefixSeperator, hidePrefix, fixGeometry)))
          }
        }
      }
    }
  }

  /**
   * Helper function to create list of headers
   */
  def addHeaders(row: JsArray, headers: ListBuffer[Header], ignore: Array[String], prefix: String, prefixSeperator: String, hidePrefix: Boolean, fixGeometry: Boolean) {
    row.value.indices.withFilter(i => !(ignore contains getPrefix(i, prefix, prefixSeperator))).foreach(i => {
      val s = i.toString
      row(i) match {
        case y: JsArray => {
          headers.find(f => f.key.equals(s)) match {
            case Some(Header(s, Left(x))) => Logger.error("Duplicate key [" + s + "] detected")
            case Some(Header(s, Right(x))) => addHeaders(y, x, ignore, getPrefix(i, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            case None => {
              val x = ListBuffer.empty[Header]
              headers += Header(s, Right(x))
              addHeaders(y, x, ignore, getPrefix(i, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            }
          }
        }
        case y: JsObject => {
          headers.find(f => f.key.equals(s)) match {
            case Some(Header(s, Left(x))) => Logger.error("Duplicate key [" + s + "] detected")
            case Some(Header(s, Right(x))) => addHeaders(y, x, ignore, getPrefix(i, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            case None => {
              val x = ListBuffer.empty[Header]
              headers += Header(s, Right(x))
              addHeaders(y, x, ignore, getPrefix(i, prefix, prefixSeperator), prefixSeperator, hidePrefix, fixGeometry)
            }
          }
        }
        case y => {
          headers.find(f => f.key.equals(s)) match {
            case Some(Header(s, Left(x))) => None
            case Some(Header(s, Right(x))) => Logger.error("Duplicate key [" + s + "] detected")
            case _ => headers += Header(s, Left(getHeader(i, prefix, prefixSeperator, hidePrefix, fixGeometry)))
          }
        }
      }
    })
  }

  /**
   * Helper function to create the text that is printed as header
   */
  def getHeader(key: Any, prefix: String, prefixSeperator: String, hidePrefix: Boolean, fixGeometry: Boolean): String = {
    if (fixGeometry && prefix.endsWith("geometry" + prefixSeperator + "coordinates")) {
      (key.toString, hidePrefix) match {
        case ("0", true) => "longitude"
        case ("0", false) => getPrefix("longitude", prefix, prefixSeperator)
        case ("1", true) => "latitude"
        case ("1", false) => getPrefix("latitude", prefix, prefixSeperator)
        case ("2", true) => "altitude"
        case ("2", false) => getPrefix("altitude", prefix, prefixSeperator)
        case (_, true) => key.toString
        case (_, false) => getPrefix(key, prefix, prefixSeperator)
      }
    } else {
      if (hidePrefix)
        key.toString
      else
        getPrefix(key, prefix, prefixSeperator)
    }
  }

  /**
   * Helper function to print data row of JSON Object.
   */
  def printRow(row: JsObject, headers: ListBuffer[Header], prefix: String, prefixSeperator: String) : String = {
    var result = ""
    for(h <- headers) {
      (row.\(h.key), h.value) match {
        case (x: JsArray, Right(y)) => result += "," + printRow(x, y, getPrefix(h.key, prefix, prefixSeperator), prefixSeperator)
        case (x: JsObject, Right(y)) => result += "," + printRow(x, y, getPrefix(h.key, prefix, prefixSeperator), prefixSeperator)
        case (x: JsUndefined, Left(_)) => result += ","
        case (x, Right(y)) => result += "," + printRow(JsObject(Seq.empty), y, getPrefix(h.key, prefix, prefixSeperator), prefixSeperator)
        case (x, Left(_)) => result += "," + x
      }
    }
    result.substring(1)
  }

  /**
   * Helper function to print data row of JSON Array.
   */
  def printRow(row: JsArray, headers: ListBuffer[Header], prefix: String, prefixSeperator: String) : String  = {
    var result = ""
    for(h <- headers) {
      val i = h.key.toInt
      (row(i), h.value) match {
        case (x: JsArray, Right(y)) => result += "," + printRow(x, y, getPrefix(prefix, h.key, prefixSeperator), prefixSeperator)
        case (x: JsObject, Right(y)) => result += "," + printRow(x, y, getPrefix(prefix, h.key, prefixSeperator), prefixSeperator)
        case (x: JsUndefined, Left(_)) => result += ","
        case (x, Right(y)) => result += "," + printRow(JsObject(Seq.empty), y, getPrefix(h.key, prefix, prefixSeperator), prefixSeperator)
        case (x, Left(y)) => result += "," + x
      }
    }
    result.substring(1)
  }

  /**
   * Class to hold a json key, and any subkeys
   */
  case class Header(key: String, value: Either[String, ListBuffer[Header]])

  def getConfig = PermissionAction(Permission.ViewGeoStream) { implicit request =>
    Logger.debug("Getting config")
    current.plugin[PostgresPlugin] match {
      case Some(plugin) if plugin.isEnabled => {
        Ok(Json.obj(
          "userAgreement" -> Json.toJson(AppConfiguration.getTermsOfServicesText),
          "sensorsTitle" -> Json.toJson(AppConfiguration.getSensorsTitle),
          "sensorTitle" -> Json.toJson(AppConfiguration.getSensorTitle),
          "parametersTitle" -> Json.toJson(AppConfiguration.getParametersTitle),
          "parameterTitle" -> Json.toJson(AppConfiguration.getParameterTitle)
        ))
      }
      case _ => pluginNotEnabled
    }
  }

  private def createGeoJson(longlat: List[Double]): JsObject = {
    val lon = longlat(1)
    val lat = longlat(0)
    val alt = if (longlat.length == 3) {longlat(2)} else {0.0}
    Json.obj(
      "type" -> "Point",
      "coordinates" -> Json.arr(lon, lat, alt)
    )
  }
}
