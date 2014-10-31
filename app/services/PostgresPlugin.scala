/**
 *
 */
package services

import play.api.{ Plugin, Logger, Application }
import play.api.Play.current
import java.sql.DriverManager
import java.util.Properties
import java.sql.Timestamp
import java.sql.Statement
import play.api.libs.json.{Json, JsObject}
import util.Parsers

/**
 * Postgres connection and simple geoindex methods.
 *
 * @author Luigi Marini
 *
 */
class PostgresPlugin(application: Application) extends Plugin {

  var conn: java.sql.Connection = null

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
    val ps = conn.prepareStatement("INSERT INTO datapoints(start_time, end_time, stream_id, data, geog, created) VALUES(?, ?, ?, CAST(? AS json), ST_SetSRID(ST_MakePoint(?, ?, ?), 4326), NOW());")
    ps.setTimestamp(1, new Timestamp(start.getTime))
    if (end.isDefined) ps.setTimestamp(2, new Timestamp(end.get.getTime))
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
    val ps = conn.prepareStatement("INSERT INTO sensors(name, geog, created, metadata) VALUES(?, ST_SetSRID(ST_MakePoint(?, ?, ?), 4326), NOW(), CAST(? AS json));")
    ps.setString(1, name)
    ps.setDouble(2, lon)
    ps.setDouble(3, lat)
    ps.setDouble(4, alt)
    ps.setString(5, metadata)
    ps.executeUpdate()
    ps.close()
  }

  def searchSensors(geocode: Option[String]): Option[String] = {
    val parts = geocode match {
      case Some(x) => x.split(",")
      case None => Array[String]()
    }
    var i = 0
    var query = "WITH stream_info AS (" +
    			"SELECT sensor_id, start_time, end_time, unnest(params) AS param FROM streams" +
    			") " +
    			"SELECT row_to_json(t, true) FROM (" +
    			"SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, 'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, to_char(min(stream_info.start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS min_start_time, to_char(max(stream_info.end_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS max_end_time, array_agg(distinct stream_info.param) as parameters " +
    			"FROM sensors " +
    			"LEFT OUTER JOIN stream_info ON stream_info.sensor_id = sensors.gid "
	  if (parts.length == 3) {
      query += "WHERE ST_DWithin(geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
    } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
      query += "WHERE ST_Covers(ST_MakePolygon(ST_MakeLine(ARRAY["
      i = 0
      while (i < parts.length) {
        query += "ST_MakePoint(?, ?), "
        i += 2
      }
      query += "ST_MakePoint(?, ?)])), geog)"
    }
    query += " GROUP BY id"
    query += " ORDER BY name"
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
    var data = "[ "
    while (rs.next()) {
      if (data != "[ ") data += ","
      data += rs.getString(1)
    }
    data += "]"
    rs.close()
    st.close()
    if (data == "null") { // FIXME
      Logger.debug("Searching NONE")
      None
    } else Some(data)
  }
  
  def getSensor(id: String): Option[String] = {
    val query = "WITH stream_info AS (" +
    			"SELECT sensor_id, start_time, end_time, unnest(params) AS param FROM streams WHERE sensor_id=?" +
    			") " +
    			"SELECT row_to_json(t, true) AS my_sensor FROM (" +
    			"SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, 'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, to_char(min(stream_info.start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS min_start_time, to_char(max(stream_info.end_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') as max_end_time, array_agg(distinct stream_info.param) as parameters " +
    			"FROM sensors " +
    			"LEFT OUTER JOIN stream_info ON stream_info.sensor_id = sensors.gid " +
    			"WHERE sensors.gid=?" +
    			"GROUP BY gid) AS t"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    st.setInt(2, id.toInt)
    Logger.debug("Sensors get statement: " + st)
    val rs = st.executeQuery()
    var data = ""
    while (rs.next()) {
      data += rs.getString(1)
    }
    rs.close()
    st.close()
    if (data == "") {
      None
    } else if (data == "null") { // FIXME
      Logger.debug("Result is NULL")
      None
    } else Some(data)
  }
  
  def getSensorStreams(id: String): Option[String] = {
    var data = ""
    val query = "SELECT array_to_json(array_agg(t),true) As my_places FROM " +
      "(SELECT streams.gid As stream_id FROM streams WHERE sensor_id=?) As t;"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    Logger.debug("Get streams by sensor statement: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
    }
    rs.close()
    st.close()
    if (data == "") {
      None
    } else if (data == "null") { // FIXME
      Logger.debug("Result is NULL")
      None
    } else Some(data)
  }
  
  def updateSensorStats(sensor_id: Option[String]) {
    var query = "WITH stream_sub1 AS (" +
    			"SELECT stream_id, start_time, end_time, json_object_keys(data) AS param FROM datapoints" +
    			")," +
    			"stream_sub2 AS (" +
    			"SELECT stream_id, min(start_time) AS start_time, max(end_time) AS end_time, array_agg(distinct param) as params FROM stream_sub1 GROUP BY stream_id" +
    			")" +
    			"UPDATE streams SET start_time=stream_sub2.start_time, end_time=stream_sub2.end_time, params=stream_sub2.params FROM stream_sub2  WHERE gid=stream_id"
    if (sensor_id.isDefined) query += " AND sensor_id = ?"
    val st = conn.prepareStatement(query)
    if (sensor_id.isDefined) st.setInt(1, sensor_id.get.toInt)
    Logger.debug("updateSensorStats statement: " + st)
    st.execute()
	  st.close()
  }
  
  def getSensorStats(id: String): Option[String] = {
    val query = "WITH stream_info AS (" +
    			"SELECT sensor_id, start_time, end_time, unnest(params) AS param FROM streams WHERE sensor_id=?" +
    			") " +
    			"SELECT row_to_json(t, true) AS my_sensor FROM (" +
    			"SELECT to_char(min(start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') As min_start_time, to_char(max(start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') As max_start_time, array_agg(distinct param) AS parameters FROM stream_info" +
    			") As t;"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    Logger.debug("Get streams by sensor statement: " + st)
    val rs = st.executeQuery()
    var data = ""
    while (rs.next()) {
      data += rs.getString(1)
    }
    rs.close()
    st.close()
    if (data == "") {
      None
    } else if (data == "null") { // FIXME
      Logger.debug("Result is NULL")
      None
    } else Some(data)
  }

  def createStream(name: String, geotype: String, lat: Double, lon: Double, alt: Double, metadata: String, stream_id: String): String = {
    val ps = conn.prepareStatement("INSERT INTO streams(name, geog, created, metadata, sensor_id) VALUES(?, ST_SetSRID(ST_MakePoint(?, ?, ?), 4326), NOW(), CAST(? AS json), ?);", Statement.RETURN_GENERATED_KEYS)
    ps.setString(1, name)
    ps.setDouble(2, lon)
    ps.setDouble(3, lat)
    ps.setDouble(4, alt)
    ps.setString(5, metadata)
    ps.setInt(6, stream_id.toInt)
    ps.executeUpdate()
    val rs = ps.getGeneratedKeys
    rs.next()
    val generatedKey = rs.getInt(1)
    Logger.debug("Key returned from getGeneratedKeys(): "+ generatedKey)
    rs.close()
    ps.close()
    generatedKey.toString
  }

  def updateStreamStats(stream_id: Option[String]) {
    var query = "WITH stream_sub1 AS (" +
    			"SELECT stream_id, start_time, end_time, json_object_keys(data) AS param FROM datapoints"
    if (stream_id.isDefined) query += " WHERE stream_id = ?"    
	  query += ")," +
			 "stream_sub2 AS (" +
			 "SELECT stream_id, min(start_time) AS start_time, max(end_time) AS end_time, array_agg(distinct param) as params FROM stream_sub1 GROUP BY stream_id" +
			 ")" +
			 "UPDATE streams SET start_time=stream_sub2.start_time, end_time=stream_sub2.end_time, params=stream_sub2.params FROM stream_sub2  WHERE gid=stream_id"
    val st = conn.prepareStatement(query)
    if (stream_id.isDefined) st.setInt(1, stream_id.get.toInt)
    Logger.debug("updateStreamStats statement: " + st)
    st.execute()
	  st.close()
  }

  def searchStreams(geocode: Option[String]): Option[String] = {
    val parts = geocode match {
      case Some(x) => x.split(",")
      case None => Array[String]()
    }
    var data = ""
    var i = 0
    var query = "SELECT array_to_json(array_agg(t),true) As my_places FROM " +
      "(SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, 'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, sensor_id::text, start_time, end_time, params FROM streams"
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
      "(SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, 'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, sensor_id::text, start_time, end_time, params FROM streams WHERE gid=?) As t;"
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
  
  def searchDatapoints(since: Option[String], until: Option[String], geocode: Option[String], stream_id: Option[String], sensor_id: Option[String], source: List[String], attributes: List[String], sortByStation: Boolean): Iterator[JsObject] = {
    val parts = geocode match {
      case Some(x) => x.split(",")
      case None => Array[String]()
    }
    var query = "SELECT to_json(t) As datapoint FROM " +
      "(SELECT datapoints.gid As id, to_char(datapoints.created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, to_char(datapoints.start_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS start_time, to_char(datapoints.end_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS end_time, data As properties, 'Feature' As type, ST_AsGeoJson(1, datapoints.geog, 15, 0)::json As geometry, stream_id::text, sensor_id::text, sensors.name as sensor_name FROM sensors, streams, datapoints" +
      " WHERE sensors.gid = streams.sensor_id AND datapoints.stream_id = streams.gid"
//    if (since.isDefined || until.isDefined || geocode.isDefined || stream_id.isDefined) query += " WHERE "
    if (since.isDefined) query += " AND datapoints.start_time >= ?"
    if (until.isDefined) query += " AND datapoints.end_time <= ?"
    if (parts.length == 3) {
      query += " AND ST_DWithin(datapoints.geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
    } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
      query += " AND ST_Covers(ST_MakePolygon(ST_MakeLine(ARRAY["
      var j = 0
      while (j < parts.length) {
        query += "ST_MakePoint(?, ?), "
        j += 2
      }
      query += "ST_MakePoint(?, ?)])), datapoints.geog)"
    }
    // attributes
    if (attributes.nonEmpty) {
      query += " AND (? = ANY(SELECT json_object_keys(datapoints.data))"
      for (x <- 1 until attributes.size)
        query += " OR ? = ANY(SELECT json_object_keys(datapoints.data))"
      query += ")"
    }
    // data source
    if (source.nonEmpty) {
      query += " AND (? = json_extract_path_text(sensors.metadata,'type','id')"
      for (x <- 1 until source.size)
        query += " OR ? = json_extract_path_text(sensors.metadata,'type','id')"
      query += ")"
    }
    //stream
    if (stream_id.isDefined) query += " AND stream_id = ?"
    //sensor
    if (sensor_id.isDefined) query += " AND sensor_id = ?"
    query += " order by "
    if (sortByStation) {
      query += "sensor_name, "
    }
    query += "start_time asc) As t;"
    val st = conn.prepareStatement(query)
    var i = 0
    if (since.isDefined) {
      i = i + 1
      st.setTimestamp(i, new Timestamp(Parsers.parseDate(since.get).get.getMillis))
    }
    if (until.isDefined) {
      i = i + 1
      st.setTimestamp(i, new Timestamp(Parsers.parseDate(until.get).get.getMillis))
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
    if (attributes.nonEmpty) {
      for (x <- 0 until attributes.size) {
      i = i + 1
      st.setString(i, attributes(x))
      }
    }
    // sources
    if (source.nonEmpty) {
      for (x <- 0 until source.size) {
        i = i + 1
        st.setString(i, source(x))
      }
    }
    if (stream_id.isDefined) {
      i = i + 1
      st.setInt(i, stream_id.get.toInt)
    }
    if (sensor_id.isDefined) {
      i = i + 1
      st.setInt(i, sensor_id.get.toInt)
    }
    st.setFetchSize(50)
    Logger.debug("Geostream search: " + st)
    val rs = st.executeQuery()

    new Iterator[JsObject] {
      var nextObject: Option[JsObject] = None

      def hasNext = {
        if (nextObject.isDefined) {
          true
        } else if (rs.isClosed) {
          false
        } else if (!rs.next) {
          rs.close()
          st.close()
          false
        } else {
          if (attributes.isEmpty) {
            nextObject = Some(Json.parse(rs.getString(1)).as[JsObject])
          } else {
            nextObject = Some(filterProperties(Json.parse(rs.getString(1)).as[JsObject], attributes))
          }
          true
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
  
  def filterProperties(obj: JsObject, attributes: List[String]) = {
    var props = JsObject(Seq.empty)
    (obj \ "properties").asOpt[JsObject] match {
	  case Some(x) => {
	    for (f <- x.fieldSet) {
	      if (("source" == f._1) || attributes.contains(f._1)) {
	        props = props + f
	      }
	    }
	    (obj - ("properties") + ("properties", props))
	  }
	  case None => obj
    }
  }
  
  def getDatapoint(id: String): Option[String] = {
    var data = ""
    val query = "SELECT row_to_json(t,true) As my_datapoint FROM " +
      "(SELECT gid As id, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, to_char(start_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS start_time, to_char(end_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS end_time, data As properties, 'Feature' As type, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, stream_id:text FROM datapoints WHERE gid=?) As t;"
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
    Logger.info("Searching postgis: " + searchDatapoints(None, None, None, None, None, List.empty, List.empty, false))
  }
}
