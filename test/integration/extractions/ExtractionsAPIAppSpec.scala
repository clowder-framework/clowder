package integration


import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.libs.json.Json
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.Logger
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play._
import play.api.{Play, Application}

/*
 * Based on http://stackoverflow.com/questions/15133794/writing-a-test-case-for-file-uploads-in-play-2-1-and-scala
 *
 * Functional Test for DTS File Upload
 * 
 * @author Smruti Padhy
 */

//@DoNotDiscover
class ExtractionsAPIAppSpec extends PlaySpec with ConfiguredApp with FakeMultipartUpload {

  lazy val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
  lazy val workingDir = System.getProperty("user.dir")

  var ncsaLogoFileId: String = ""
  var morrowPlotFileId: String = ""

  case class FileName(size: String, datecreated: String, id: String, contenttype: String, filename: String)

  implicit val fileReads: Reads[FileName] = (
      (__ \ "size").read[String] and
      (__ \ "date-created").read[String] and
      (__ \ "id").read[String] and
      (__ \ "content-type").read[String] and
      (__ \ "filename").read[String]
    )(FileName.apply _)


  "The Extractions API Spec" must {
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
  }

  "The Extractions API Spec" must {
    "respond to the Upload File URL" in {
      val fileurl = "http://www.ncsa.illinois.edu/assets/img/logos_ncsa.png"
      val request = FakeRequest(POST, "/api/extractions/upload_url?key=" + secretKey).withJsonBody(Json.toJson(Map("fileurl" -> fileurl)))
      val result = route(request).get
      info("Status=" + status(result))
      status(result) mustEqual OK
      info("contentType=" + contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include("id")
      ncsaLogoFileId = contentAsJson(result).\("id").as[String]
      info("contentAsString" + contentAsString(result))

    }
    
    "respond to the Upload File" in {
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/extractions/morrowplots.jpg")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      val req = FakeRequest(POST, "/api/extractions/upload_file?key=" + secretKey).
        withFileUpload("File", file1, "image/jpg")
      val result = route(req).get

      info("Status=" + status(result))
      status(result) mustEqual OK
      info("contentType=" + contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include("id")
      morrowPlotFileId = contentAsJson(result).\("id").as[String]
      info("contentAsString" + contentAsString(result))
    }

     "respond to the getExtractorNamesAction" in {
      val Some(result) = route(FakeRequest(GET, "/api/extractions/extractors_names?key=" + secretKey))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("Extractors")
      info("content"+contentAsString(result))
    }

    "respond to the getExtractorServerIPsAction" in {
      val Some(result) = route(FakeRequest(GET, "/api/extractions/servers_ips?key=" + secretKey))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("Servers")
      info("content"+contentAsString(result))
    }

    "respond to the getExtractorSupportedInputTypesAction" in {
      val Some(result) = route(FakeRequest(GET, "/api/extractions/supported_input_types?key=" + secretKey))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("InputTypes")
      info("content"+contentAsString(result))
    }

    "respond to the getDTSRequests" in {
      val Some(result) = route(FakeRequest(GET, "/api/extractions/requests?key=" + secretKey))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
    }

    "respond to the removeFile(id:UUID) function routed by DELETE /api/files/:id for morrow-plots file  " in {
      // After finding specific "id" of file call RESTful API to get JSON information
      val Some(result_get) = route(FakeRequest(DELETE, "/api/files/" + morrowPlotFileId + "?key=" + secretKey))
      info("Status_Get="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
    }


    "respond to the removeFile(id:UUID) function routed by DELETE /api/files/:id for logos_ncsa file " in {
      // After finding specific "id" of file call RESTful API to get JSON information
      val Some(result_get) = route(FakeRequest(DELETE, "/api/files/" + ncsaLogoFileId + "?key=" + secretKey))
      info("Status_Get="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
    }

  }
}