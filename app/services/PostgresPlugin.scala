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
      conn = DriverManager.getConnection(url)
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
      val createTable = "CREATE TABLE IF NOT EXISTS geoindex(gid serial PRIMARY KEY, title text, geog geography(Point, 4326), timestamp timestamp, data json, stream_id varchar(255));"
      val stmt = conn.createStatement()
      stmt.execute(createTable)
      stmt.close()
    } catch {
      case unknown: Throwable => Logger.error("Error creating table in postgres: " + unknown)
    }
  }

  def add(title: String, timestamp: java.util.Date, data: String, lat: Double, lon: Double) {
    val ps = conn.prepareStatement("INSERT INTO geoindex(title, timestamp, data, geog) VALUES(?, ?, CAST(? AS json), ST_SetSRID(ST_MakePoint(?, ?), 4326));")
    ps.setString(1, title)
    ps.setDate(2, new java.sql.Date(timestamp.getTime()))
    ps.setString(3, data)
    ps.setDouble(4, lon)
    ps.setDouble(5, lat)
    ps.executeUpdate()
    ps.close()
  }

  def search(since: Option[String], until: Option[String], geocode: Option[String]): String = {
    var data = ""
    var query = "SELECT array_to_json(array_agg(t),true) As my_places FROM " +
    		"(SELECT gid, title, timestamp, data, ST_AsGeoJson(1, geog, 15, 0)::json As geog FROM geoindex"
    if (since.isDefined || until.isDefined || geocode.isDefined) query+= " WHERE "
    if (since.isDefined) query += "timestamp >= ? "
    if (since.isDefined && (until.isDefined || geocode.isDefined)) query += " AND "
    if (until.isDefined) query += "timestamp <= ? "
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
    Logger.debug("Geostream search: " + st)
    val rs = st.executeQuery()
    while (rs.next()) {
      data += rs.getString(1)
      System.out.println(data)
    }
    rs.close()
    st.close()
    data
  }
  
  def test() {
    add("Urbana", new java.util.Date(), """{"value":"test"}""", 40.110588, -88.207270)
    Logger.info("Searching postgis: " + search(None, None, None))
  }

}