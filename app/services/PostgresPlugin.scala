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
import java.sql.Statement

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
    val parts = geocode match {
      case Some(x) => x.split(",")
      case None => Array[String]()
    }
    var i = 0
    var data = ""
    var query = "SELECT array_to_json(array_agg(t),true) As my_places FROM " +
      "(SELECT gid As id, name, created, 'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry FROM sensors"
    if (parts.length == 3) {
      query += " WHERE ST_DWithin(geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
    } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
      query += " WHERE ST_Covers(ST_MakePolygon(ST_MakeLine(ARRAY["
      i = 0
      while (i < parts.length) {
        query += "ST_MakePoint(?, ?), "
        i += 2
      }
      query += "ST_MakePoint(?, ?)])), geog)"
    }
    query += ") As t;"
    val st = conn.prepareStatement(query)
    i = 0
    if (parts.length == 3) {
      st.setDouble(i + 1, parts(1).toDouble)
      st.setDouble(i + 2, parts(0).toDouble)
      st.setDouble(i + 3, parts(2).toDouble * 1000)
    } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
      while (i < parts.length) {
        st.setDouble(i + 1, parts(i+1).toDouble)
        st.setDouble(i + 2, parts(i).toDouble)
        i += 2
      }
      st.setDouble(i + 1, parts(1).toDouble)
      st.setDouble(i + 2, parts(0).toDouble)
    }
    st.setFetchSize(50)
    Logger.debug("Sensors search statement: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
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
      "(SELECT gid As id, name, created, 'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry FROM sensors WHERE gid=?) As t;"
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
  
  def getSensorStreams(id: String): Option[String] = {
    var data = ""
    val query = "SELECT array_to_json(array_agg(t),true) As my_places FROM " +
      "(SELECT streams.gid As stream_id FROM sensors, streams WHERE sensors.gid = ? AND sensors.gid = streams.sensor_id GROUP BY streams.gid) As t;"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    Logger.debug("Get streams by sensor statement: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
      Logger.debug("Sensors found: " + data)
    }
    rs.close()
    st.close()
    Logger.debug("Searching streams by sensor result: " + data)
    if (data == "null") { // FIXME
      Logger.debug("Searching NONE")
      None
    } else Some(data)
  }
  
  def getSensorDateRange(id: String): Option[String] = {
    var data = ""
    val query = "SELECT to_json(t) FROM " + 
    			"(SELECT min(datapoints.start_time) As min_start_time, max(datapoints.start_time) As max_start_time FROM " +
    			"sensors, streams, datapoints WHERE sensors.gid = ? AND sensors.gid = streams.sensor_id AND datapoints.stream_id = streams.gid) As t;"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    Logger.debug("Get streams by sensor statement: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
      Logger.debug("Sensor date range: " + data)
    }
    rs.close()
    st.close()
    Logger.debug("Getting sensors statistics: " + data)
    if (data == "null") { // FIXME
      Logger.debug("Searching NONE")
      None
    } else Some(data)
  }
  
  def getSensorParameters(id: String): Option[String] = {
    var data = ""
    val query = "SELECT to_json(unique_values) FROM (SELECT array(SELECT json_object_keys(datapoints.data) As key " +
                "FROM sensors, streams, datapoints WHERE sensors.gid = ? AND sensors.gid = streams.sensor_id AND datapoints.stream_id = streams.gid GROUP BY key) As parameters) As unique_values;"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    Logger.debug("Get streams by parameter statement: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
      Logger.debug("Sensors parameters: " + data)
    }
    rs.close()
    st.close()
    Logger.debug("Getting sensors parameters: " + data)
    if (data == "null") { // FIXME
      Logger.debug("Searching NONE")
      None
    } else Some(data)
  
  }

  def createStream(name: String, geotype: String, lat: Double, lon: Double, alt: Double, metadata: String, stream_id: String): String = {
    val ps = conn.prepareStatement("INSERT INTO streams(name, geog, created, metadata, sensor_id) VALUES(?, ST_SetSRID(ST_MakePoint(?, ?, ?), 4326), ?, CAST(? AS json), ?);", Statement.RETURN_GENERATED_KEYS)
    ps.setString(1, name)
    ps.setDouble(2, lon)
    ps.setDouble(3, lat)
    ps.setDouble(4, alt)
    ps.setTimestamp(5, new Timestamp(new Date().getTime()))
    ps.setString(6, metadata)
    ps.setInt(7, stream_id.toInt)
    ps.executeUpdate()
    val rs = ps.getGeneratedKeys()
    rs.next()
    val generatedKey = rs.getInt(1)
    Logger.debug("Key returned from getGeneratedKeys():"+ generatedKey);
    rs.close();
    ps.close()
    return generatedKey.toString()
  }

  def searchStreams(geocode: Option[String]): Option[String] = {
    val parts = geocode match {
      case Some(x) => x.split(",")
      case None => Array[String]()
    }
    var data = ""
    var i = 0
    var query = "SELECT array_to_json(array_agg(t),true) As my_places FROM " +
      "(SELECT gid As id, name, created, 'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, sensor_id::text FROM streams"
    if (parts.length == 3) {
      query += " WHERE ST_DWithin(geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
    } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
      query += " WHERE ST_Covers(ST_MakePolygon(ST_MakeLine(ARRAY["
      i = 0
      while (i < parts.length) {
        query += "ST_MakePoint(?, ?), "
        i += 2
      }
      query += "ST_MakePoint(?, ?)])), geog)"
    }
    query += ") As t;"
    val st = conn.prepareStatement(query)
    i = 0
    if (parts.length == 3) {
      st.setDouble(i + 1, parts(1).toDouble)
      st.setDouble(i + 2, parts(0).toDouble)
      st.setDouble(i + 3, parts(2).toDouble * 1000)
    } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
      while (i < parts.length) {
        st.setDouble(i + 1, parts(i+1).toDouble)
        st.setDouble(i + 2, parts(i).toDouble)
        i += 2
      }
      st.setDouble(i + 1, parts(1).toDouble)
      st.setDouble(i + 2, parts(0).toDouble)
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
      "(SELECT gid As id, name, created, 'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, sensor_id::text FROM streams WHERE gid=?) As t;"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    Logger.debug("Streams get statement: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
    }
    rs.close()
    st.close()
    Logger.debug("Searching streams result: " + data)
    if (data == "null") { // FIXME
      Logger.debug("Searching NONE")
      None
    } else Some(data)
  }

    
  def deleteStream(id: Integer): Boolean = {
	val deleteStream = "DELETE from streams where gid = ?"
    val st = conn.prepareStatement(deleteStream)
    st.setInt(1, id)
    st.execute()
    st.close()
    val deleteDatapoints = "DELETE from datapoints where stream_id = ?"
    val st2 = conn.prepareStatement(deleteDatapoints)
    st2.setInt(1, id)
    st2.execute()
    st2.close()
    true
  }
  
  
  def dropAll(): Boolean = {
    val deleteSensors = "DELETE from sensors"
    val st = conn.prepareStatement(deleteSensors)
    st.executeUpdate()
    st.close()
    val deleteStreams = "DELETE from streams"
    val st2 = conn.prepareStatement(deleteStreams)
    st2.executeUpdate()
    st2.close()
    val deleteDatapoints = "DELETE from datapoints"
    val st3 = conn.prepareStatement(deleteDatapoints)
    st3.executeUpdate()
    st3.close()
    true
  }
  
  def counts(): (Int, Int, Int) = {
    var counts = (0, 0, 0)
    val countQuery = "SELECT (SELECT COUNT(DISTINCT gid) FROM sensors) AS sensors,(SELECT COUNT(DISTINCT gid) FROM streams) AS streams,(SELECT COUNT(DISTINCT gid) FROM datapoints) AS datapoints"
    val st = conn.prepareStatement(countQuery)
    val rs = st.executeQuery()
    while (rs.next()) {
      counts = (rs.getInt(1), rs.getInt(2), rs.getInt(3))
      System.out.println(counts)
    }
    rs.close()
    st.close()
    counts
  }
  
  def searchDatapoints(since: Option[String], until: Option[String], geocode: Option[String], stream_id: Option[String], source: List[String], attributes: List[String]): Option[String] = {
    val parts = geocode match {
      case Some(x) => x.split(",")
      case None => Array[String]()
    }
    var data = ""
    var query = "SELECT array_to_json(array_agg(t),true) As my_places FROM " +
      "(SELECT datapoints.gid As id, start_time, end_time, data As properties, 'Feature' As type, ST_AsGeoJson(1, datapoints.geog, 15, 0)::json As geometry, stream_id::text FROM sensors, streams, datapoints" +
      " WHERE sensors.gid = streams.sensor_id AND datapoints.stream_id = streams.gid AND "
//    if (since.isDefined || until.isDefined || geocode.isDefined || stream_id.isDefined) query += " WHERE "
    if (since.isDefined) query += "start_time >= ? "
    if (since.isDefined && (until.isDefined || geocode.isDefined)) query += " AND "
    if (until.isDefined) query += "start_time <= ? "
    if ((since.isDefined || until.isDefined) && geocode.isDefined) query += " AND "
    if (parts.length == 3) {
      query += " ST_DWithin(datapoints.geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
    } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
      query += " ST_Covers(ST_MakePolygon(ST_MakeLine(ARRAY["
      var j = 0
      while (j < parts.length) {
        query += "ST_MakePoint(?, ?), "
        j += 2
      }
      query += "ST_MakePoint(?, ?)])), datapoints.geog)"
    }
    // attributes
    if (!attributes.isEmpty) for (x <- 0 until attributes.size) {
     query += " AND "
     query += "? = ANY(SELECT json_object_keys(datapoints.data))"
    }
    // data source
    if (!source.isEmpty) for (x <- 0 until source.size) {
      query += " AND ? = json_extract_path_text(sensors.metadata,'type','id')"
    }
    //stream
    if (stream_id.isDefined) query += "stream_id = ?"
    query += " order by start_time asc) As t;"
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
    if (parts.length == 3) {
      st.setDouble(i + 1, parts(1).toDouble)
      st.setDouble(i + 2, parts(0).toDouble)
      st.setDouble(i + 3, parts(2).toDouble * 1000)
      i += 3
    } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
      var j = 0
      while (j < parts.length) {
        st.setDouble(i + 1, parts(j+1).toDouble)
        st.setDouble(i + 2, parts(j).toDouble)
        i += 2
        j += 2
      }
      st.setDouble(i + 1, parts(1).toDouble)
      st.setDouble(i + 2, parts(0).toDouble)
      i += 2
    }
    // attributes
    if (!attributes.isEmpty) {
      for (x <- 0 until attributes.size) {
      i = i + 1
      st.setString(i, attributes(x))
      }
    }
    // sources
    if (!source.isEmpty) {
      for (x <- 0 until source.size) {
        i = i + 1
        st.setString(i, source(x))
      }
    }
    if (stream_id.isDefined) {
      i = i + 1
      st.setInt(i, stream_id.get.toInt)
    }
    st.setFetchSize(50)
    Logger.debug("Geostream search: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
    }
    rs.close()
    st.close()
    Logger.trace("Searching datapoints result: " + data)
    if (data == "null") None // FIXME
    else Some(data)
  }
  
  def getDatapoint(id: String): Option[String] = {
    var data = ""
    val query = "SELECT row_to_json(t,true) As my_datapoint FROM " +
      "(SELECT gid As id, start_time, end_time, data As properties, 'Feature' As type, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, stream_id:text FROM datapoints WHERE gid=?) As t;"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    Logger.debug("Datapoints get statement: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
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
    val query = "SELECT array_to_json(array_agg(t),true) As my_places FROM (SELECT gid As id, start_time, end_time, data, ST_AsGeoJson(1, geog, 15, 0)::json As geometry FROM datapoints"
  }

  def test() {
    addDatapoint(new java.util.Date(), None, "Feature", """{"value":"test"}""", 40.110588, -88.207270, 0.0, "http://test/stream")
    Logger.info("Searching postgis: " + searchDatapoints(None, None, None, None, List.empty, List.empty))
  }
}
