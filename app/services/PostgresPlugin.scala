/**
 *
 */
package services

import play.api.{ Plugin, Logger, Application }
import play.api.Play.current
import com.typesafe.config.ConfigFactory
import java.sql.DriverManager
import java.util.Properties
import java.util.Date
import java.text.SimpleDateFormat
import java.sql.Timestamp

/**
 * Postgres connection and simple geoindex methods.
 *
 * @author Luigi Marini
 *
 */
class PostgresPlugin(application: Application) extends Plugin {

  var conn: java.sql.Connection = null;
  val formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")

  override def onStart() {
    Logger.debug("Starting Postgres Plugin")

    val configuration = play.api.Play.configuration
    val host = configuration.getString("postgres.host").getOrElse("localhost")
    val port = configuration.getString("postgres.port").getOrElse("5432")
    val db = configuration.getString("postgres.db").getOrElse("geostream")
    val user = configuration.getString("postgres.user").getOrElse("")
    val password = configuration.getString("postgres.password").getOrElse("")

    try {
      Class.forName("org.postgresql.Driver")
      val url = "jdbc:postgresql://" + host + ":" + port + "/" + db
      val props = new Properties()
      if (!user.equals("")) props.setProperty("user", user)
      if (!password.equals("")) props.setProperty("password", password)
      conn = DriverManager.getConnection(url, props)
      //      conn.setAutoCommit(false)
      Logger.debug("Connected to " + url)
    } catch {
      case unknown: Throwable => Logger.error("Error connecting to postgres: " + unknown)
    }
  }

  override def onStop() {
    Logger.debug("Shutting down Postgres Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("postgresplugin").filter(_ == "disabled").isDefined
  }

  def addDatapoint(start: java.util.Date, end: Option[java.util.Date], geoType: String, data: String, lat: Double, lon: Double, alt: Double, stream_id: String) {
    val ps = conn.prepareStatement("INSERT INTO datapoints(start_time, end_time, stream_id, data, geog) VALUES(?, ?, ?, CAST(? AS json), ST_SetSRID(ST_MakePoint(?, ?, ?), 4326));")
    ps.setTimestamp(1, new Timestamp(start.getTime()))
    if (end.isDefined) ps.setTimestamp(2, new Timestamp(end.get.getTime()))
    else ps.setDate(2, null)
    ps.setInt(3, stream_id.toInt)
    ps.setString(4, data)
    ps.setDouble(5, lon)
    ps.setDouble(6, lat)
    ps.setDouble(7, alt)
    ps.executeUpdate()
    ps.close()
  }

  def createSensor(name: String, geoType: String, lat: Double, lon: Double, alt: Double, metadata: String) {
    val ps = conn.prepareStatement("INSERT INTO sensors(name, geog, created, metadata) VALUES(?, ST_SetSRID(ST_MakePoint(?, ?, ?), 4326), ?, CAST(? AS json));")
    ps.setString(1, name)
    ps.setDouble(2, lon)
    ps.setDouble(3, lat)
    ps.setDouble(4, alt)
    ps.setTimestamp(5, new Timestamp(new Date().getTime()))
    ps.setString(6, metadata)
    ps.executeUpdate()
    ps.close()
  }

  def searchSensors(geocode: Option[String]): Option[String] = {
    var data = ""
    var query = "SELECT array_to_json(array_agg(t),true) As my_places FROM " +
      "(SELECT gid, name, created, 'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry FROM sensors"
    if (geocode.isDefined) query += " WHERE ST_DWithin(geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
    query += ") As t;"
    val st = conn.prepareStatement(query)
    var i = 0
    if (geocode.isDefined) {
      val parts = geocode.get.split(",")
      st.setDouble(i + 1, parts(1).toDouble)
      st.setDouble(i + 2, parts(0).toDouble)
      st.setDouble(i + 3, parts(2).toDouble * 1000)
    }
    st.setFetchSize(50)
    Logger.debug("Sensors search statement: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
      Logger.debug("Sensor found: " + data)
    }
    rs.close()
    st.close()
    Logger.debug("Searching sensors result: " + data)
    if (data == "null") { // FIXME
      Logger.debug("Searching NONE")
      None
    } else Some(data)
  }
  
  def getSensor(id: String): Option[String] = {
    var data = ""
    val query = "SELECT row_to_json(t,true) As my_sensor FROM " +
      "(SELECT gid, name, created, 'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry FROM sensors WHERE gid=?) As t;"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    Logger.debug("Sensors get statement: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
      Logger.debug("Sensor found: " + data)
    }
    rs.close()
    st.close()
    Logger.debug("Searching sensors result: " + data)
    if (data == "null") { // FIXME
      Logger.debug("Searching NONE")
      None
    } else Some(data)
  }

  def createStream(name: String, geotype: String, lat: Double, lon: Double, alt: Double, metadata: String, stream_id: String) {
    val ps = conn.prepareStatement("INSERT INTO streams(name, geog, created, metadata, sensor_id) VALUES(?, ST_SetSRID(ST_MakePoint(?, ?, ?), 4326), ?, CAST(? AS json), ?);")
    ps.setString(1, name)
    ps.setDouble(2, lon)
    ps.setDouble(3, lat)
    ps.setDouble(4, alt)
    ps.setTimestamp(5, new Timestamp(new Date().getTime()))
    ps.setString(6, metadata)
    ps.setInt(7, stream_id.toInt)
    ps.executeUpdate()
    ps.close()
  }

  def searchStreams(geocode: Option[String]): Option[String] = {
    var data = ""
    var query = "SELECT array_to_json(array_agg(t),true) As my_places FROM " +
      "(SELECT gid, name, created, 'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, sensor_id FROM streams"
    if (geocode.isDefined) query += " WHERE ST_DWithin(geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
    query += ") As t;"
    val st = conn.prepareStatement(query)
    var i = 0
    if (geocode.isDefined) {
      val parts = geocode.get.split(",")
      st.setDouble(i + 1, parts(1).toDouble)
      st.setDouble(i + 2, parts(0).toDouble)
      st.setDouble(i + 3, parts(2).toDouble * 1000)
    }
    st.setFetchSize(50)
    Logger.debug("Sensors search statement: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
      Logger.debug("Sensor found: " + data)
    }
    rs.close()
    st.close()
    Logger.debug("Searching streams result: " + data)
    if (data == "null") None // FIXME
    else Some(data)
  }
  
  def getStream(id: String): Option[String] = {
    var data = ""
    val query = "SELECT row_to_json(t,true) As my_stream FROM " +
      "(SELECT gid, name, created, 'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry FROM streams WHERE gid=?) As t;"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    Logger.debug("Streams get statement: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
      Logger.debug("Streams found: " + data)
    }
    rs.close()
    st.close()
    Logger.debug("Searching streams result: " + data)
    if (data == "null") { // FIXME
      Logger.debug("Searching NONE")
      None
    } else Some(data)
  }

  def searchDatapoints(since: Option[String], until: Option[String], geocode: Option[String]): Option[String] = {
    var data = ""
    var query = "SELECT array_to_json(array_agg(t),true) As my_places FROM " +
      "(SELECT gid, start_time, end_time, data As properties, 'Feature' As type, ST_AsGeoJson(1, geog, 15, 0)::json As geometry FROM datapoints"
    if (since.isDefined || until.isDefined || geocode.isDefined) query += " WHERE "
    if (since.isDefined) query += "start_time >= ? "
    if (since.isDefined && (until.isDefined || geocode.isDefined)) query += " AND "
    if (until.isDefined) query += "start_time <= ? "
    if ((since.isDefined || until.isDefined) && geocode.isDefined) query += " AND "
    if (geocode.isDefined) query += "ST_DWithin(geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
    query += ") As t;"
    val st = conn.prepareStatement(query)
    var i = 0
    if (since.isDefined) {
      i = i + 1
      st.setTimestamp(i, new Timestamp(formatter.parse(since.get).getTime))
    }
    if (until.isDefined) {
      i = i + 1
      st.setTimestamp(i, new Timestamp(formatter.parse(until.get).getTime))
    }
    if (geocode.isDefined) {
      val parts = geocode.get.split(",")
      st.setDouble(i + 1, parts(1).toDouble)
      st.setDouble(i + 2, parts(0).toDouble)
      st.setDouble(i + 3, parts(2).toDouble * 1000)
    }
    st.setFetchSize(50)
    Logger.trace("Geostream search: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
      System.out.println(data)
    }
    rs.close()
    st.close()
    data
    if (data == "null") None // FIXME
    else Some(data)
  }
  
  def getDatapoint(id: String): Option[String] = {
    var data = ""
    val query = "SELECT row_to_json(t,true) As my_datapoint FROM " +
      "(SELECT gid, start_time, end_time, data As properties, 'Feature' As type, ST_AsGeoJson(1, geog, 15, 0)::json As geometry FROM datapoints WHERE gid=?) As t;"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    Logger.debug("Datapoints get statement: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
      Logger.debug("Datapoints found: " + data)
    }
    rs.close()
    st.close()
    Logger.debug("Searching datapoints result: " + data)
    if (data == "null") { // FIXME
      Logger.debug("Searching NONE")
      None
    } else Some(data)
  }

  def listSensors() {
    val query = "SELECT array_to_json(array_agg(t),true) As my_places FROM (SELECT gid, start_time, end_time, data, ST_AsGeoJson(1, geog, 15, 0)::json As geometry FROM datapoints"
  }

  def test() {
    addDatapoint(new java.util.Date(), None, "Feature", """{"value":"test"}""", 40.110588, -88.207270, 0.0, "http://test/stream")
    Logger.info("Searching postgis: " + searchDatapoints(None, None, None))
  }

}