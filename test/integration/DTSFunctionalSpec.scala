package integration

import org.scalatestplus.play.OneServerPerSuite
import org.scalatestplus.play.PlaySpec
import play.api.test.FakeApplication
import play.api.test.Helpers
import play.api.libs.ws.WS
import play.api.test.FutureAwaits
import org.apache.http.client.HttpClient
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import play.api.libs.json.Json
import org.apache.http.entity.StringEntity
import play.api.libs.json.JsObject

class DTSFunctionalSpec extends PlaySpec with OneServerPerSuite {
  val excludedPlugins = List("services.VersusPlugin")
  implicit override lazy val app: FakeApplication = FakeApplication(withoutPlugins = excludedPlugins)

  "The OneServerPerSuite trait" must {
    "provide a FakeApplication" in {
      app.configuration.getString("ehcacheplugin") mustBe Some("disabled")
    }

    "provide the port number" in {
      port mustBe Helpers.testServerPort
    }
    "provide an actual running server" in {

      val client = new DefaultHttpClient()

      val requestUrl = "http://localhost:" + port + "/api/extractions/upload_url?key=r1ek3rs"

      val httpPost = new HttpPost(requestUrl);

      httpPost.setHeader("Content-type", "application/json")
      val simg = "http://isda.ncsa.illinois.edu/drupal/sites/default/files/images/IMG_1289.JPG"
      //httpPost.setEntity(new StringEntity(Json.stringify(Json.toJson(Map("fileurl"->"http://www.ncsa.illinois.edu/assets/img/logos_ncsa.png")))))
      httpPost.setEntity(new StringEntity(Json.stringify(Json.toJson(Map("fileurl" -> simg)))))
      import java.io.BufferedReader

      import java.io.InputStreamReader

      //val response = client.execute(httpGet);
      val response = client.execute(httpPost)
      val x = response.getEntity().getContent()
      val y = response.getStatusLine().getStatusCode()
      y mustBe (200)
      if (y == 200) {
        var br = new BufferedReader(new InputStreamReader(x))
        val fid = br.readLine
        fid must include("id")
        info("fid : " + fid)
        val js = Json.parse(fid)
        val js1 = js.\("id")
        info("js=" + Json.stringify(js1))
        val js2 = Json.stringify(js1)
        val id = js2.substring(1, js2.length - 1)
        info("id=" + id)
        val client2 = new DefaultHttpClient()
        val requestUrl2 = "http://localhost:" + port + "/api/extractions/" + id + "/status"
        var httpGet = new HttpGet(requestUrl2)
        // import following package
        import scala.util.control._

        // create a Breaks object as follows
        val loop = new Breaks;
        var t=10
        // Keep the loop inside breakable as follows
        loop.breakable {

          while (t>0) {
            httpGet = new HttpGet(requestUrl2)
            httpGet.setHeader("Accept", "application/json")
            val response2 = client2.execute(httpGet)
            val x2 = response2.getEntity().getContent()
            val y2 = response2.getStatusLine().getStatusCode()
            y2 mustBe (200)
            var br2 = new BufferedReader(new InputStreamReader(x2))
            val s2 = br2.readLine
            br2.close
            if (y2 == 200) {
              var sp = Json.parse(s2)
              info("s2" + Json.stringify(Json.parse(s2)))
              if (Json.stringify(sp.\("Status")).contains("Done")) {
                loop.break()
              } //end of Done
            } //end of 200
            t=t-1
          } //end of while
        } //loop break
        val client3 = new DefaultHttpClient()
        val requestUrl3 = "http://localhost:" + port + "/api/files/" + id + "/technicalmetadatajson"
        var httpGetmd = new HttpGet(requestUrl3)
        httpGet.setHeader("Accept", "application/json")
        val response3 = client3.execute(httpGetmd)
        val x3 = response3.getEntity().getContent()
        val y3 = response3.getStatusLine().getStatusCode()
        y3 mustBe (200)
        var br3 = new BufferedReader(new InputStreamReader(x3))
        val s3 = br3.readLine
        var tmd = Json.stringify(Json.parse(s3))
        var ttmd = "\"exif:ColorSpace\" : \"1\" , \"exif:ComponentsConfiguration\" : \"1, 2, 3, 0\" , \"exif:Compression\" : \"6\" , \"exif:ExifImageLength\" : \"299\" , \"exif:ExifImageWidth\" : \"640\" , \"exif:ExifOffset\" : \"102\" , \"exif:ExifVersion\" : \"48, 50, 50, 49\" , \"exif:FlashPixVersion\" : \"48, 49, 48, 48\" , \"exif:JPEGInterchangeFormat\" : \"286\" , \"exif:JPEGInterchangeFormatLength\" : \"7402\" , \"exif:Orientation\" : \"1\" , \"exif:ResolutionUnit\" : \"2\" , \"exif:SceneCaptureType\" : \"0\" , \"exif:thumbnail:ResolutionUnit\" : \"2\" , \"exif:thumbnail:XResolution\" : \"72/1\" , \"exif:thumbnail:YResolution\" : \"72/1\" , \"exif:XResolution\" : \"72/1\" , \"exif:YCbCrPositioning\" : \"1\" , \"exif:YResolution\" : \"72/1\""
        s3 must include(ttmd)
      }
    }
  }

}