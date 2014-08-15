package integration

import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.Logger
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.PlaySpec
import play.api.Play

/*
 * Functional test for ExtractionAPI Controller - Router test
 * @author Smruti Padhy
 * 
 */


class ExtractionAPIControllerFunctionalSpec extends PlaySpec with OneAppPerSuite{
 val excludedPlugins = List(
    "services.RabbitmqPlugin",
    "services.VersusPlugin")
 
  implicit override lazy val app: FakeApplication = FakeApplication(withoutPlugins = excludedPlugins)
 	
 "The OneAppPerSuite for Extraction API Controller Router test" must {
 "respond to the getExtractorNamesAction" in {
      val Some(result) = route(FakeRequest(GET, "/api/extractions/extractors_names"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("Extractors")
      info("content"+contentAsString(result))
    }
 "respond to the getExtractorServerIPsAction" in {
      val Some(result) = route(FakeRequest(GET, "/api/extractions/servers_ips"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("Servers")
      info("content"+contentAsString(result))
    }
 "respond to the getExtractorSupportedInputTypesAction" in {
      val Some(result) = route(FakeRequest(GET, "/api/extractions/supported_input_types"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("InputTypes")
      info("content"+contentAsString(result))
    }
 "respond to the getDTSRequests" in {
      val Some(result) = route(FakeRequest(GET, "/api/extractions/requests"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
    }
 }
}