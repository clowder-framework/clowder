package integration

import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.Logger
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.PlaySpec
import play.api.Play
import org.apache.http.entity.mime.content.ContentBody
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.commons.io.output.ByteArrayOutputStream
import org.apache.http.entity.mime.content.FileBody
import play.api.libs.json.JsObject
import scala.io.Source
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.FileReader
import java.io.File
import play.api.http.Writeable
import play.api.mvc.MultipartFormData
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.Codec
import org.apache.http.entity.ContentType

import play.api.test._
import org.scalatest._
import org.scalatestplus.play._
import play.api.{Play, Application}


/*
 * Integration tests for Previews API - Router test
 * @author Eugene Roeder
 * 
 */

//@DoNotDiscover
class PreviewsAPIAppSpec extends PlaySpec with ConfiguredApp with FakeMultipartUpload {

  // Defining a model to read files from Json content returned from API
  case class FileName(size: String, datecreated: String, id: String, contenttype: String, filename: String)

  implicit val fileReads: Reads[FileName] = (
    (__ \ "size").read[String] and
    (__ \ "date-created").read[String] and
    (__ \ "id").read[String] and
    (__ \ "content-type").read[String] and
    (__ \ "filename").read[String]
  )(FileName.apply _)

 	
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
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
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
      info("contentAsString" + contentAsString(result))
    }

  "respond to the upload() and download() function routed by POST/GET /api/previews" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
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
      info("contentAsString" + contentAsString(result))

      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)

      val id = readableString.toString().split(":")(1).split("\"")(1)
      info("id value " + id)

      val req2 = FakeRequest(GET, "/api/previews/" + id + "?key=" + secretKey)
      val result2 = route(req).get

      info("Status=" + status(result2))
      status(result2) mustEqual OK
      info("contentType=" + contentType(result2))
      contentType(result2) mustEqual Some("application/json")
      //contentAsString(result2) must include("id")
      info("contentAsString" + contentAsString(result2)) 
    }


    "respond to the uploadMetadata(id: UUID) and getMetadata(id: UUID) function routed by POST/GET /api/previews/:id/metadata" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/previews/morrowplots-preview-meta.jpg")
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
      info("contentAsString" + contentAsString(result))

      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)

      val id = readableString.toString().split(":")(1).split("\"")(1)
      info("id value " + id)

      //link up json file here before fake request.
      val file2 = new java.io.File(workingDir + "/test/data/previews/data-test-general.json")
      if (file2.isFile && file2.exists) {
        Logger.debug("File2 is File:True")
      }
      info("File Pathing " + file2.toString())
      val json_data_from_file_source = Source.fromFile(file2.toString())
      val json_data_from_file_lines = json_data_from_file_source.mkString
      json_data_from_file_source.close()

      // Place file string into a JSON object
      val json_meta: JsValue = Json.parse(json_data_from_file_lines)
      val readableString_meta: String = Json.prettyPrint(json_meta)
      info("Pretty JSON format")
      info(readableString_meta)

      // Send JSON object into RESTful API and read response
      val Some(result_post) = route(FakeRequest(POST, "/api/previews/" + id + "/metadata?key=" + secretKey).withJsonBody(json_meta))
      status(result_post) mustEqual OK
      info("Status_Post="+status(result_post))
      status(result_post) mustEqual OK
      info("contentType_Post="+contentType(result_post))
      contentType(result_post) mustEqual Some("application/json")

      // val req2 = FakeRequest(GET, "/api/previews/" + id + "/metadata?key=" + secretKey)
      // val result2 = route(req).get

      // info("Status=" + status(result2))
      // status(result2) mustEqual OK
      // info("contentType=" + contentType(result2))
      // contentType(result2) mustEqual Some("application/json")
      // //contentAsString(result2) must include("id")
      // info("contentAsString" + contentAsString(result2)) 

    }

    "respond to the attachPreview(file_id:UUID, preview:UUID) function routed by POST /api/files/:file_id/previews/:p_id  " in {
      //link up json file here before fake request.
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/files"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("filename")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[FileName]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[FileName], _) => list
          info("Mapping file model to Json worked")
          info("Number of files in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.filename contains "morrowplots.jpg").toString().split(",")(2))
          val file_id = list.filter(_.filename contains "morrowplots.jpg").toString().split(",")(2)


          val workingDir = System.getProperty("user.dir")
          info("Working Directory: " + workingDir)
          val file1 = new java.io.File(workingDir + "/test/data/previews/morrowplots-preview-1.jpg")
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
          info("contentAsString" + contentAsString(result))

          val json: JsValue = Json.parse(contentAsString(result))
          val readableString: String = Json.prettyPrint(json)
          info("Pretty JSON format")
          info(readableString)

          val preview_id = readableString.toString().split(":")(1).split("\"")(1)
          info("preview_id value " + preview_id)


          //link up json file here before fake request.
          val file2 = new java.io.File(workingDir + "/test/data/previews/data-test-preview.json")
          if (file2.isFile && file2.exists) {
            Logger.debug("File2 is File:True")
          }
          info("File Pathing " + file2.toString())
          val json_data_from_file_source = Source.fromFile(file2.toString())
          val json_data_from_file_lines = json_data_from_file_source.mkString
          json_data_from_file_source.close()

          // Place file string into a JSON object
          val json_meta: JsValue = Json.parse(json_data_from_file_lines)
          val readableString_meta: String = Json.prettyPrint(json_meta)
          info("Pretty JSON format")
          info(readableString_meta)


          // After finding specific "id" of file call RESTful API to get JSON information
          info("POST /api/files/" + file_id + "/previews/" + preview_id)
          val Some(result_post) = route(FakeRequest(POST, "/api/files/" + file_id + "/previews/" + preview_id + "?key=" + secretKey).withJsonBody(json_meta))

          info("Status_Post="+status(result_post))
          status(result_post) mustEqual OK
          info("contentType_Post="+contentType(result_post))
          contentType(result_post) mustEqual Some("application/json")
          val json2: JsValue = Json.parse(contentAsString(result_post))
          val readableString2: String = Json.prettyPrint(json2)
          info("Pretty JSON format")
          info(readableString2)
        case e: JsError => {
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }


   "respond to the filePreviewsList(id: UUID) function routed by GET /api/files/:file_id/listpreviews" in {
      //link up json file here before fake request.
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/files"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("filename")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[FileName]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[FileName], _) => list
          info("Mapping file model to Json worked")
          info("Number of files in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.filename contains "morrowplots.jpg").toString().split(",")(2))
          val file_id = list.filter(_.filename contains "morrowplots.jpg").toString().split(",")(2)


          // After finding specific "id" of file call RESTful API to get JSON information
          info("GET /api/files/" + file_id + "/listpreviews")
          val Some(result_get) = route(FakeRequest(GET, "/api/files/" + file_id + "/listpreviews?key=" + secretKey))

          info("Status_get="+status(result_get))
          status(result_get) mustEqual OK
          info("contentType_get="+contentType(result_get))
          contentType(result_get) mustEqual Some("application/json")
          val json: JsValue = Json.parse(contentAsString(result_get))
          val readableString: String = Json.prettyPrint(json)
          info("Pretty JSON format")
          info(readableString)
        case e: JsError => {
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }


   "respond to the getPreviews(id: UUID) function routed by GET /api/files/:file_id/getPreviews" in {
      //link up json file here before fake request.
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/files"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("filename")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[FileName]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[FileName], _) => list
          info("Mapping file model to Json worked")
          info("Number of files in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.filename contains "morrowplots.jpg").toString().split(",")(2))
          val file_id = list.filter(_.filename contains "morrowplots.jpg").toString().split(",")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          info("GET /api/files/" + file_id + "/getPreviews")
          val Some(result_get) = route(FakeRequest(GET, "/api/files/" + file_id + "/getPreviews?key=" + secretKey))

          info("Status_Get="+status(result_get))
          status(result_get) mustEqual OK
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("application/json")
          val json: JsValue = Json.parse(contentAsString(result_get))
          val readableString: String = Json.prettyPrint(json)
          info("Pretty JSON format")
          info(readableString)
        case e: JsError => {
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }

    "respond to the removeFile(id:UUID) function routed by DELETE /api/files/:id  " in {
      //link up json file here before fake request.
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/files"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("filename")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[FileName]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[FileName], _) => list
          info("Mapping file model to Json worked")
          info("Number of files in System " + list.length.toString())
          info(list.toString())

          info(list.filter(_.filename contains "morrowplots.jpg").toString().split(",")(2))
          val id = list.filter(_.filename contains "morrowplots.jpg").toString().split(",")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          info("DELETE /api/files/" + id)
          val Some(result_get) = route(FakeRequest(DELETE, "/api/files/" + id + "?key=" + secretKey))
          info("Status_Get="+status(result_get))
          status(result_get) mustEqual OK
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("application/json")
          val json: JsValue = Json.parse(contentAsString(result_get))
          val readableString: String = Json.prettyPrint(json)
          info("Pretty JSON format")
          info(readableString)
        case e: JsError => {
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
   }


 }
}