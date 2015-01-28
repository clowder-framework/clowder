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

/*
 * DTS Functional tests
 *    
 * @author Smruti Padhy
 * 
 */

class FilesAPIServerSpec extends PlaySpec with OneServerPerSuite with GivenWhenThen {
  val excludedPlugins = List("services.VersusPlugin")
  implicit override lazy val app: FakeApplication = FakeApplication(withoutPlugins = excludedPlugins)

  "The OneServerPerSuite trait" must {
    "provide a FakeApplication" in {
      app.configuration.getString("ehcacheplugin") mustBe Some("disabled")
    }

    "provide the port number" in {
      port mustBe Helpers.testServerPort
    }
    "provide an actual running server for DTS functional test" in {

      info("Status=Working FilesAPIServerSpec Test")

    }// End of test

    }
}