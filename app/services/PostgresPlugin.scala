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
import util.Parsers
import play.api.libs.json._


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

    updateDatabase()
  }

  override def onStop() {
    Logger.debug("Shutting down Postgres Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("postgresplugin").filter(_ == "disabled").isDefined
  }

  def addDatapoint(start: java.util.Date, end: Option[java.util.Date], geoType: String, data: String, lat: Double, lon: Double, alt: Double, stream_id: String) {
    val ps = conn.prepareStatement("INSERT INTO datapoints(start_time, end_time, stream_id, data, geog, created) VALUES(?, ?, ?, CAST(? AS jsonb), ST_SetSRID(ST_MakePoint(?, ?, ?), 4326), NOW());")
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

  def searchSensors(geocode: Option[String], sensor_name: Option[String]): Option[String] = {
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
      if (sensor_name.isDefined) query += " AND name = ?"
    } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
      query += "WHERE ST_Covers(ST_MakePolygon(ST_MakeLine(ARRAY["
      i = 0
      while (i < parts.length) {
        query += "ST_MakePoint(?, ?), "
        i += 2
      }
      query += "ST_MakePoint(?, ?)])), geog)"
      if (sensor_name.isDefined) query += " AND name = ?"
    } else if (parts.length == 0) {
      if (sensor_name.isDefined) query += " WHERE name = ?"
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
      if (sensor_name.isDefined) st.setString(i + 4, sensor_name.getOrElse(""))
    } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
      while (i < parts.length) {
        st.setDouble(i + 1, parts(i+1).toDouble)
        st.setDouble(i + 2, parts(i).toDouble)
        i += 2
      }
      st.setDouble(i + 1, parts(1).toDouble)
      st.setDouble(i + 2, parts(0).toDouble)
      if (sensor_name.isDefined) st.setString(i + 3, sensor_name.getOrElse(""))
    } else if (parts.length == 0 && sensor_name.isDefined) {
      st.setString(1, sensor_name.getOrElse(""))
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

  def updateSensorMetadata(id: String, data: String): Option[String] = {
    val query = "SELECT row_to_json(t, true) AS my_sensor FROM (" +
      "SELECT metadata As properties FROM sensors " +
      "WHERE gid=?) AS t"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    Logger.debug("Sensors get statement: " + st)
    val rs = st.executeQuery()
    var sensorData = ""
    while (rs.next()) {
      sensorData += rs.getString(1)
    }
    rs.close()
    st.close()

    val oldDataJson = Json.parse(sensorData).as[JsObject]
    val newDataJson = Json.parse(data).as[JsObject]

    val jsonTransformer = (__ \ 'properties).json.update(
      __.read[JsObject].map{ o => o ++ newDataJson }
    )
    val updatedJSON = oldDataJson.transform(jsonTransformer).get

    val query2 = "UPDATE sensors SET metadata = CAST(? AS json) WHERE gid = ?"
    val st2 = conn.prepareStatement(query2)
    st2.setString(1, Json.stringify((updatedJSON \ "properties")))
    st2.setInt(2, id.toInt)
    Logger.debug("Sensors put statement: " + st2)
    val rs2 = st2.executeUpdate()
    st2.close()
    getSensor(id)
  }

  /**
   * Operates like the HTTP PATCH method, but uses HTTP PUT.
   * PUT typically replaces everything in the field getting updated
   * PATCH typically only modifies the changes you pass in
   * This will not delete any values in metadata,
   * even if a single tree of a nested object is passed, all siblings will remain.
   * @param id stream id [String]
   * @param data to be updated [JsValue]
   * @return returns the entire stream response with updated values from getStream(id)
   */
  def patchStreamMetadata(id: String, data: String): Option[String] = {
    val query = "SELECT row_to_json(t, true) AS my_stream FROM (" +
      "SELECT metadata As properties FROM streams " +
      "WHERE gid=?) AS t"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    Logger.debug("Streams get statement: " + st)
    val rs = st.executeQuery()
    var streamData = ""
    while (rs.next()) {
      streamData += rs.getString(1)
    }
    rs.close()
    st.close()

    val oldDataJson = Json.parse(streamData).as[JsObject]
    val newDataJson = Json.parse(data).as[JsObject]

    val jsonTransformer = (__ \ 'properties).json.update(
      __.read[JsObject].map{ o => o ++ newDataJson }
    )
    val updatedJSON = oldDataJson.transform(jsonTransformer).get

    val query2 = "UPDATE streams SET metadata = CAST(? AS json) WHERE gid = ?"
    val st2 = conn.prepareStatement(query2)
    st2.setString(1, Json.stringify((updatedJSON \ "properties")))
    st2.setInt(2, id.toInt)
    Logger.debug("Stream put statement: " + st2)
    val rs2 = st2.executeUpdate()
    st2.close()
    getStream(id)
  }

  /**
   * Retrieve links for sensor pages on da
   * @param ids sensor ids
   * @return a list of tuples, first element is sensor name, second is sensor url on dashboard
   */
  def getDashboardSensorURLs(ids: List[String]): List[(String, String)] = {
    val base = play.api.Play.configuration.getString("geostream.dashboard.url").getOrElse("http://localhost:9000")
    val sensorsJson = ids.map(id => Json.parse(getSensor(id).getOrElse("{}")))
    List.tabulate(sensorsJson.size) { i =>
      val name = (sensorsJson(i) \ "name").as[String]
      (name, base + "#detail/location/" + name + "/")
    }
  }
  
  def getSensorStreams(id: String): Option[String] = {
    var data = ""
    val query = "SELECT array_to_json(array_agg(t),true) As my_places FROM " +
      "(SELECT streams.gid As stream_id, streams.name As name FROM streams WHERE sensor_id=?) As t;"
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
  
  def getSensorStats(id: String): Option[String] = {
    val query = "WITH stream_info AS (" +
    			"SELECT sensor_id, start_time, end_time, unnest(params) AS param FROM streams WHERE sensor_id=?" +
    			") " +
    			"SELECT row_to_json(t, true) AS my_sensor FROM (" +
    			"SELECT to_char(min(start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') As min_start_time, to_char(max(end_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') As max_end_time, array_agg(distinct param) AS parameters FROM stream_info" +
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

  def searchStreams(geocode: Option[String], stream_name: Option[String]): Option[String] = {
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
      if (stream_name.isDefined) {
        query += " AND name = ?"
      }
    } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
      query += " WHERE ST_Covers(ST_MakePolygon(ST_MakeLine(ARRAY["
      i = 0
      while (i < parts.length) {
        query += "ST_MakePoint(?, ?), "
        i += 2
      }
      query += "ST_MakePoint(?, ?)])), geog)"
      if (stream_name.isDefined) {
        query += " AND name = ?"
      }
    } else if (parts.length == 0) {
      if (stream_name.isDefined) {
        query += " WHERE name = ?"
      }
    }
    query += ") As t;"
    val st = conn.prepareStatement(query)
    i = 0
    if (parts.length == 3) {
      st.setDouble(i + 1, parts(1).toDouble)
      st.setDouble(i + 2, parts(0).toDouble)
      st.setDouble(i + 3, parts(2).toDouble * 1000)
      if (stream_name.isDefined) {
        st.setString(i + 4, stream_name.getOrElse(""))
      }
    } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
      while (i < parts.length) {
        st.setDouble(i + 1, parts(i+1).toDouble)
        st.setDouble(i + 2, parts(i).toDouble)
        i += 2
      }
      st.setDouble(i + 1, parts(1).toDouble)
      st.setDouble(i + 2, parts(0).toDouble)
      if (stream_name.isDefined) {
        st.setString(i + 3, stream_name.getOrElse(""))
      }
    } else if (parts.length == 0 && stream_name.isDefined) {
      st.setString(1, stream_name.getOrElse(""))
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
    if (data == "null" || data == "") { // FIXME
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

  def deleteSensor(id: Integer): Boolean = {
    // get the stream id's for this sensor
    var streams = Array[Int]()
    val query = "DELETE FROM datapoints USING streams WHERE stream_id IN (SELECT gid FROM streams WHERE sensor_id = ?);" +
      "DELETE FROM streams WHERE gid IN (SELECT gid FROM streams WHERE sensor_id = ?);" +
      "DELETE FROM sensors where gid = ?;"
    val st = conn.prepareStatement(query)
    st.setInt(1, id.toInt)
    st.setInt(2, id.toInt)
    st.setInt(3, id.toInt)
    Logger.debug("Deleting datapoints, streams and sensor statement: " + st)
    st.executeUpdate()
    st.close()
    true
  }

  def deleteDatapoint(gid: Integer): Boolean = {
    val query = "DELETE FROM datapoints where gid = ?"
    val st = conn.prepareStatement(query)
    st.setInt(1,gid)
    Logger.debug("Deleting datapoint statement: " + st)
    st.execute()
    st.close
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
      query += " AND (datapoints.data ?? ?"
      for (x <- 1 until attributes.size)
        query += " OR (datapoints.data ?? ?)"
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

  // ----------------------------------------------------------------------
  // CODE TO UPDATE STATISTICS OF STREAMS AND SENSORS
  // ----------------------------------------------------------------------

  def updateSensorStats(sensor_id: Option[String]) {
    // always update the empty streams list first
    updateEmptyStats()

    // next update the streams associated with the sensor
    var query = "UPDATE streams SET start_time=n.start_time, end_time=n.end_time, params=n.params FROM ("
    query += "  SELECT stream_id, min(datapoints.start_time) AS start_time, max(datapoints.end_time) AS end_time, array_agg(distinct keys) AS params"
    if (!sensor_id.isDefined) {
      query += "    FROM datapoints, jsonb_object_keys(data) data(keys)"
    } else {
      query += "    FROM datapoints, jsonb_object_keys(data) data(keys), streams"
      query += "    WHERE streams.gid=datapoints.stream_id AND streams.sensor_id=?"
    }
    query += "    GROUP by stream_id) n"
    query += "  WHERE n.stream_id=streams.gid;"

    val st = conn.prepareStatement(query)
    if (sensor_id.isDefined) st.setInt(1, sensor_id.get.toInt)
    Logger.debug("updateSensorStats statement: " + st)
    st.execute()
    st.close()
  }

  def updateStreamStats(stream_id: Option[String]) {
    // always update the empty streams list first
    updateEmptyStats()

    // next update the streams
    var query = "UPDATE streams SET start_time=n.start_time, end_time=n.end_time, params=n.params FROM ("
    query += "  SELECT stream_id, min(datapoints.start_time) AS start_time, max(datapoints.end_time) AS end_time, array_agg(distinct keys) AS params"
    query += "    FROM datapoints, jsonb_object_keys(data) data(keys)"
    if (stream_id.isDefined) query += " WHERE stream_id = ?"
    query += "    GROUP BY stream_id) n"
    query += "  WHERE n.stream_id=streams.gid;"

    val st = conn.prepareStatement(query)
    if (stream_id.isDefined) st.setInt(1, stream_id.get.toInt)
    Logger.debug("updateStreamStats statement: " + st)
    st.execute()
    st.close()
  }

  def updateEmptyStats() {
    val query = "update streams set start_time=null, end_time=null, params=null " +
      "where not exists (select gid from datapoints where streams.gid=datapoints.stream_id)"

    val st = conn.prepareStatement(query)
    Logger.debug("updateEmptyStats statement: " + st)
    st.execute()
    st.close()
  }

  def test() {
    addDatapoint(new java.util.Date(), None, "Feature", """{"value":"test"}""", 40.110588, -88.207270, 0.0, "http://test/stream")
    Logger.info("Searching postgis: " + searchDatapoints(None, None, None, None, None, List.empty, List.empty, false))
  }

  // ----------------------------------------------------------------------
  // CODE TO UPDATE THE DATABASE
  // ----------------------------------------------------------------------
  def updateDatabase() {
    // update datapoints to JSONB
    updateDatapointsPropertiesToJSONB

  }

  private def updateDatapointsPropertiesToJSONB {
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

    if (!appConfig.hasPropertyValue("postgres.updates", "datapoints-properties-to-jsonb")) {
      if (System.getProperty("POSTGRESUPDATE") != null) {
        Logger.info("[PostgresUpdate] : Upgrading datapoints to jsonb.")
        val query = "ALTER TABLE datapoints ALTER COLUMN data SET DATA TYPE jsonb USING data::jsonb"
        val st = conn.prepareStatement(query)
        Logger.debug("[PostgresUpdate] : Upgrading datapoints to jsonb: " + st)
        st.execute()
        st.close()

        val query2 = "CREATE INDEX datapoints_data_idx ON datapoints USING gin (data)"
        val st2 = conn.prepareStatement(query2)
        Logger.debug("[PostgresUpdate] : Creating datapoint properties index: " + st2)
        st2.execute()
        st2.close()

        appConfig.addPropertyValue("postgres.updates", "datapoints-properties-to-jsonb")
      } else {
        Logger.warn("[PostgresUpdate] : Datapoints properties column needs to be updated to JSONB. Restart Clowder with -DPOSTGRESUPDATE=1")
      }
    }
  }
}
