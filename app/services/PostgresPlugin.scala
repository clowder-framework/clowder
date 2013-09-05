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

    setup()
    
//    test()
  }

  override def onStop() {
    Logger.debug("Shutting down Postgres Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("postgresplugin").filter(_ == "disabled").isDefined
  }
  
  def setup() {
    Logger.debug("Setting up postgres tables")
    try {
      val createTable = "CREATE TABLE IF NOT EXISTS geoindex(gid serial PRIMARY KEY, geog geography(Pointz, 4326), start_time timestamp, end_time timestamp, data json, stream_id varchar(255));"  
      val stmt = conn.createStatement()
      stmt.execute(createTable)
      val createIndex = "CREATE INDEX IF NOT EXISTS geoindex_gix ON geoindex USING GIST (geog);"
      stmt.execute(createIndex)
      stmt.close()
    } catch {
      case unknown: Throwable => Logger.error("Error creating table in postgres: " + unknown)
    }
  }

  def add(start: java.util.Date, end: Option[java.util.Date], data: String, lat: Double, lon: Double, alt: Double, stream_id: String) {
	val ps = conn.prepareStatement("INSERT INTO geoindex(start_time, end_time, stream_id, data, geog) VALUES(?, ?, ?, CAST(? AS json), ST_SetSRID(ST_MakePoint(?, ?, ?), 4326));")
	ps.setTimestamp(1, new Timestamp(start.getTime()))
	if (end.isDefined) ps.setTimestamp(2, new Timestamp(end.get.getTime()))
	else ps.setDate(2, null)
	ps.setString(3, stream_id)
	ps.setString(4, data)
	ps.setDouble(5, lon)
	ps.setDouble(6, lat)
	ps.setDouble(7, alt)
	ps.executeUpdate()
	ps.close()
  }

  def search(since: Option[String], until: Option[String], geocode: Option[String]): String = {
    var data = ""
    var query = "SELECT array_to_json(array_agg(t),true) As my_places FROM " +
    		"(SELECT gid, start_time, end_time, data, ST_AsGeoJson(1, geog, 15, 0)::json As geog FROM geoindex"
    if (since.isDefined || until.isDefined || geocode.isDefined) query+= " WHERE "
    if (since.isDefined) query += "start_time >= ? "
    if (since.isDefined && (until.isDefined || geocode.isDefined)) query += " AND "
    if (until.isDefined) query += "start_time <= ? "
    if ((since.isDefined || until.isDefined) && geocode.isDefined) query += " AND "
    if (geocode.isDefined) query += "ST_DWithin(geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
    query += ") As t;"
    val st = conn.prepareStatement(query)
    var i = 0
    if (since.isDefined) {
      i=i+1 
      st.setTimestamp(i, new Timestamp(formatter.parse(since.get).getTime))
    }
    if (until.isDefined) {
      i=i+1
      st.setTimestamp(i, new Timestamp(formatter.parse(until.get).getTime))
    }
    if (geocode.isDefined) {
      val parts = geocode.get.split(",")
      st.setDouble(i+1,parts(1).toDouble)
      st.setDouble(i+2,parts(0).toDouble)
      st.setDouble(i+3,parts(2).toDouble*1000)
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
  }
  
  def listSensors() {
    val query = "SELECT array_to_json(array_agg(t),true) As my_places FROM (SELECT gid, start_time, end_time, data, ST_AsGeoJson(1, geog, 15, 0)::json As geog FROM geoindex"
  }
  
  def test() {
    add(new java.util.Date(), None, """{"value":"test"}""", 40.110588, -88.207270, 0.0, "http://test/stream")
    Logger.info("Searching postgis: " + search(None, None, None))
  }

}