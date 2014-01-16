import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import play.api.libs.ws.WS
import play.api.Logger

/**
 * Geostreaming test.
 *
 * @author Luigi Marini
 *
 */
class GeostreamsSpec extends PlaySpecification {

  "search sensors" in new WithServer {
    val sensors = await(WS.url("http://localhost:19001/api/geostreams/sensors").get())
    Logger.debug("Result search sensors: " + sensors.body)
    sensors.status must equalTo(OK)
  }

  "search streams" in new WithServer {
    val streams = await(WS.url("http://localhost:19001/api/geostreams/streams").withQueryString("key" -> "letmein").get())
    streams.status must equalTo(OK)
  }

  "add datapoint" in new WithServer {
    val datapoints = await(WS.url("http://localhost:19001/api/geostreams/datapoints")
        .withHeaders("Content-Type"->"application/json")
        .withQueryString("key" -> "letmein")
        .post(""""{"start_time":"2013-08-25T03:00:00-08","end_time":"2013-08-25T04:00:00-08","type":"Point","geometry":{"type": "Point","coordinates":[-88.20727,40.110588,-5]},"properties":{"value":"42"},"stream_id":"http://test"}"""))
        Logger.debug("Adding datapoint " + datapoints)
        datapoints.status must equalTo(OK)
  }

  
  "search datapoints" in new WithServer {
    val datapoints = await(WS.url("http://localhost:19001/api/geostreams/datapoints").get())
    datapoints.status must equalTo(OK)
  }

}