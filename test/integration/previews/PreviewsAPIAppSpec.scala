package integration

import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.libs.json.Json
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.Logger
import org.scalatestplus.play.PlaySpec
import scala.io.Source
import org.scalatestplus.play._
import play.api.{Play, Application}


/*
 * Integration tests for Previews API - Router test
 * @author Eugene Roeder
 * 
 */

//@DoNotDiscover
class PreviewsAPIAppSpec extends PlaySpec with ConfiguredApp with FakeMultipartUpload {

  lazy val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
  lazy val workingDir = System.getProperty("user.dir")
  lazy val emptyJson = new JsObject(List.empty)

  var morrowPlotFileId: String = ""
  var morrowPlotPreviewId: String = ""

  // Defining a model to read files from Json content returned from API
  case class FileName(size: String, datecreated: String, id: String, contenttype: String, filename: String)

  implicit val fileReads: Reads[FileName] = (
    (__ \ "size").read[String] and
    (__ \ "date-created").read[String] and
    (__ \ "id").read[String] and
    (__ \ "content-type").read[String] and
    (__ \ "filename").read[String]
  )(FileName.apply _)


  case class PreviewName(id: String, filename: String, contentType: String)

  implicit val previewReads: Reads[PreviewName] = (
    (__ \ "id").read[String] and
      (__ \ "filename").read[String] and
      (__ \ "contentType").read[String]
    )(PreviewName.apply _)


  "The Previews API Spec" must {
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


  "The Previews API Spec" must {
    "respond to the Upload File" in {
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/previews/morrowplots.jpg")
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

    "respond to the upload() and download() function routed by POST/GET /api/previews" in {
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/previews/morrowplots-preview.jpg")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      val req = FakeRequest(POST, "/api/previews?key=" + secretKey).
        withFileUpload("File", file1, "image/jpg")
      val result = route(req).get

      info("Status=" + status(result))
      status(result) mustEqual OK
      info("contentType=" + contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include("id")
      morrowPlotPreviewId = contentAsJson(result).\("id").as[String]
      info("contentAsString" + contentAsString(result))

      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)

      val id = readableString.toString.split(":")(1).split("\"")(1)
      info("id value " + id)

      // TODO this needs be fixed when CATS-120 is fixed.
      val req2 = FakeRequest(GET, "/api/previews/" + id + "?key=" + secretKey)
      val result2 = route(req2).get

      info("Status=" + status(result2))
      status(result2) mustEqual OK
      info("contentType=" + contentType(result2))
      contentType(result2) mustEqual Some("image/jpeg")
      //contentAsString(result2) must include("id")
      info("contentAsString" + contentAsString(result2))
    }


    "respond to the uploadMetadata(id: UUID) and getMetadata(id: UUID) function routed by POST/GET /api/previews/:id/metadata" in {
      val file2 = new java.io.File(workingDir + "/test/data/previews/data-test-general.json")
      if (file2.isFile && file2.exists) {
        Logger.debug("File2 is File:True")
      }
      info("File Pathing " + file2.toString)
      val json_data_from_file_source = Source.fromFile(file2.toString)
      val json_data_from_file_lines = json_data_from_file_source.mkString
      json_data_from_file_source.close()

      // Place file string into a JSON object
      val json_meta: JsValue = Json.parse(json_data_from_file_lines)
      val readableString_meta: String = Json.prettyPrint(json_meta)
      info("Pretty JSON format")
      info(readableString_meta)

      // Send JSON object into RESTful API and read response
      val Some(result_post) = route(FakeRequest(POST, "/api/previews/" + morrowPlotPreviewId + "/metadata?key=" + secretKey).withJsonBody(json_meta))
      status(result_post) mustEqual OK
      info("Status_Post=" + status(result_post))
      status(result_post) mustEqual OK
      info("contentType_Post=" + contentType(result_post))
      contentType(result_post) mustEqual Some("application/json")
    }

    "respond to the attachPreview(file_id:UUID, preview:UUID) function routed by POST /api/files/:file_id/previews/:p_id  " in {
      val Some(result_post) = route(FakeRequest(POST, "/api/files/" + morrowPlotFileId + "/previews/" + morrowPlotPreviewId + "?key=" + secretKey).withJsonBody(emptyJson))
      info("Status_Post=" + status(result_post))
      status(result_post) mustEqual OK
      info("contentType_Post=" + contentType(result_post))
      contentType(result_post) mustEqual Some("application/json")
      val json2: JsValue = Json.parse(contentAsString(result_post))
      val readableString2: String = Json.prettyPrint(json2)
      info("Pretty JSON format")
      info(readableString2)
    }


    "respond to the filePreviewsList(id: UUID) function routed by GET /api/files/:file_id/listpreviews" in {
      val Some(result_get) = route(FakeRequest(GET, "/api/files/" + morrowPlotFileId + "/listpreviews?key=" + secretKey))
      info("Status_get=" + status(result_get))
      status(result_get) mustEqual OK
      info("contentType_get=" + contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
    }


    "respond to the getPreviews(id: UUID) function routed by GET /api/files/:file_id/getPreviews" in {
      val Some(result_get) = route(FakeRequest(GET, "/api/files/" + morrowPlotFileId + "/getPreviews?key=" + secretKey))
      info("Status_Get=" + status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get=" + contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      // TODO check morrowPlotPreviewId is returned
    }

    "respond to the list() function routed by GET /api/previews" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/previews?key=" + secretKey))
      info("Status=" + status(result))
      status(result) mustEqual OK
      info("contentType=" + contentType(result))
      contentType(result) mustEqual Some("application/json")
      //contentAsString(result) must include ("File")
      info("content" + contentAsString(result))
      // TODO check morrowPlotPreviewId is returned
    }

    "respond to the removePreview(id:UUID) function routed by DELETE /api/previews/:id for morrowplots preview file " in {
      val Some(result_get) = route(FakeRequest(DELETE, "/api/previews/" + morrowPlotPreviewId + "?key=" + secretKey))
      info("Status_Get=" + status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get=" + contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
    }

    "respond to the removeFile(id:UUID) function routed by DELETE /api/files/:id  " in {
      val Some(result_get) = route(FakeRequest(DELETE, "/api/files/" + morrowPlotFileId + "?key=" + secretKey))
      info("Status_Get=" + status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get=" + contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
    }
  }
}