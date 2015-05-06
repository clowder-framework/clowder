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
import org.scalatest.DoNotDiscover
import org.json.simple.parser.JSONParser
import org.json.simple.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import scala.util.control._
import org.scalatest.GivenWhenThen
import java.io.FileReader

import play.api.test._
import org.scalatest._
import org.scalatestplus.play._
import play.api.{Play, Application}


/*
 * DTS Functional tests
 *    
 * @author Smruti Padhy
 * 
 */

//@DoNotDiscover
class DTSExtractionsAPIAppSpec extends PlaySpec with OneServerPerSuite with GivenWhenThen {
 

  "The DTS Extractions API Spec" must {
    "provide a FakeApplication" in {
      app.configuration.getString("ehcacheplugin") mustBe Some("disabled")
    }
    "make the FakeApplication available implicitly" in {
      def getConfig(key: String)(implicit app: Application) = app.configuration.getString(key)
      getConfig("ehcacheplugin") mustBe Some("disabled")
    }
    "start the FakeApplication" in {
      Play.maybeApplication mustBe Some(app)
    }
    "provide the port number" in {
      port mustBe Helpers.testServerPort
    }
    "provide an actual running server for DTS functional test" in {

      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val client = new DefaultHttpClient()
      val requestUrl = "http://localhost:" + port + "/api/extractions/upload_url?key=" + secretKey
      val httpPost = new HttpPost(requestUrl)
      httpPost.setHeader("Content-type", "application/json")

      val test_img = "http://isda.ncsa.illinois.edu/drupal/sites/default/files/images/IMG_1289.JPG"
      httpPost.setEntity(new StringEntity(Json.stringify(Json.toJson(Map("fileurl" -> test_img)))))

      val response = client.execute(httpPost)
      val responseContent = response.getEntity().getContent()
      val responseStatus = response.getStatusLine().getStatusCode()
      responseStatus mustBe (200)
      if (responseStatus == 200) {
        var br = new BufferedReader(new InputStreamReader(responseContent))
        val fileIdJson = br.readLine
        fileIdJson must include("id")
        info("Response recieved from the Server : " + fileIdJson)

        val jsfileId = Json.parse(fileIdJson).\("id")
        val sfileId = Json.stringify(jsfileId)
        val id = sfileId.substring(1, sfileId.length - 1)
        info("Obtained fileId from the response: " + id)

        val requestUrl2 = "http://localhost:" + port + "/api/extractions/" + id + "/status"
        var httpGet = new HttpGet(requestUrl2)

        val loop = new Breaks;
        var t = 20
        // Keep the loop inside breakable 
        loop.breakable {
          while (t > 0) {
            httpGet.setHeader("Accept", "application/json")
            val response2 = client.execute(httpGet)
            val responseContent2 = response2.getEntity().getContent()
            val responseStatus2 = response2.getStatusLine().getStatusCode()
            responseStatus2 mustBe (200)
            if (responseStatus2 == 200) {
              var br2 = new BufferedReader(new InputStreamReader(responseContent2))
              val s2 = br2.readLine
              br2.close
              var sp = Json.parse(s2)
              info("status : " + Json.stringify(Json.parse(s2)))
              if (Json.stringify(sp.\("Status")).contains("Done")) {
                loop.break()
              } //end of Done
            } //end of 200
            t = t - 1
          } //end of while
        } //loop break

        if (t != 0) {
          info("Obtaining technicalmetadataJSON and comparision with test data")
          val requestUrl3 = "http://localhost:" + port + "/api/files/" + id + "/technicalmetadatajson"
          var httpGetmd = new HttpGet(requestUrl3)
          httpGetmd.setHeader("Accept", "application/json")
          val response3 = client.execute(httpGetmd)
          val responseContent3 = response3.getEntity().getContent()
          val responseStatus3 = response3.getStatusLine().getStatusCode()
          responseStatus3 mustBe (200)

          if (responseStatus3 == 200) {
            Given("Response status is OK, reading the metadata from the response content ")
            var br3 = new BufferedReader(new InputStreamReader(responseContent3))
            val s3 = br3.readLine

            When("parsing of the metadata received from the server and test metadata from the file are done")
            val technicalMD = Json.parse(s3)
            var parser = new JSONParser()
            val workingDir = System.getProperty("user.dir")
            val xs = parser.parse(new FileReader(workingDir + "/test/data/data.json")).asInstanceOf[String]
            val xs4 = xs.replaceFirst("fid", id).replaceFirst("fname", id)
            val testMD = Json.parse(xs4)

            Then("exif properties of test image must be equal to the exif properties obtained earlier for the same test image")
            val jsProperties = testMD.\("Properties")
            val jsTMD = technicalMD.\\("Properties")
            jsTMD(0).\("exif:ColorSpace") mustBe jsProperties.\("exif:ColorSpace")
            jsTMD(0).\("exif:ComponentsConfiguration") mustBe jsProperties.\("exif:ComponentsConfiguration")
            jsTMD(0).\("exif:Compression") mustBe jsProperties.\("exif:Compression")
            jsTMD(0).\("exif:ExifImageLength") mustBe jsProperties.\("exif:ExifImageLength")
            jsTMD(0).\("exif:ExifImageWidth") mustBe jsProperties.\("exif:ExifImageWidth")
            jsTMD(0).\("exif:ExifOffset") mustBe jsProperties.\("exif:ExifOffset")
            jsTMD(0).\("exif:ExifVersion") mustBe jsProperties.\("exif:ExifVersion")
            jsTMD(0).\("exif:FlashPixVersion") mustBe jsProperties.\("exif:FlashPixVersion")
            jsTMD(0).\("exif:JPEGInterchangeFormat") mustBe jsProperties.\("exif:JPEGInterchangeFormat")
            jsTMD(0).\("exif:JPEGInterchangeFormatLength") mustBe jsProperties.\("exif:JPEGInterchangeFormatLength")
            jsTMD(0).\("exif:Orientation") mustBe jsProperties.\("exif:Orientation")
            jsTMD(0).\("exif:ResolutionUnit") mustBe jsProperties.\("exif:ResolutionUnit")
            jsTMD(0).\("exif:SceneCaptureType") mustBe jsProperties.\("exif:SceneCaptureType")

            And("Channel statistics must be same")
            val jsChannelstatistics = testMD.\("Channel statistics")
            val jsTmdCS = technicalMD.\\("Channel statistics")
            jsTmdCS(0) mustBe jsChannelstatistics

            And("Image statistics must be same")
            val jsImagestatistics = testMD.\("Image statistics")
            val jsTmdIS = technicalMD.\\("Image statistics")
            jsTmdIS(0) mustBe jsImagestatistics

          } //responseStatus3
        } //(t==0)
        else {
          info("No metadata extracted")
        }
      }
    } //end of functional test upload url
  }

}