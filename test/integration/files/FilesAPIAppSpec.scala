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
 * Integration test for Files API - Router test
 * @author Eugene Roeder
 * 
 */


//@DoNotDiscover
class FilesAPIAppSpec extends PlaySpec with ConfiguredApp with FakeMultipartUpload {

  

  def printList[T](list: List[T]) {
    list match {
      case head :: tail =>
        println(head)
        printList(tail)
      case Nil =>
    }
  }


  // Defining a model to read files from Json content returned from API
  case class FileName(size: String, datecreated: String, id: String, contenttype: String, filename: String)

  implicit val fileReads: Reads[FileName] = (
    (__ \ "size").read[String] and
    (__ \ "date-created").read[String] and
    (__ \ "id").read[String] and
    (__ \ "content-type").read[String] and
    (__ \ "filename").read[String]
  )(FileName.apply _)



  "The Files API Spec" must {
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

 	
 "The Files API Spec" must {
    "respond to the Upload File URL" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val fileurl = "http://www.ncsa.illinois.edu/assets/img/logos_ncsa.png"
      val request = FakeRequest(POST, "/api/extractions/upload_url?key=" + secretKey).withJsonBody(Json.toJson(Map("fileurl" -> fileurl)))
      val result = route(request).get
      info("Status=" + status(result))
      status(result) mustEqual OK
      info("contentType=" + contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include("id")
      info("contentAsString" + contentAsString(result))
    }

    "respond to the Upload File" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/files/morrowplots.jpg")
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

  "respond to the uploadThumbnail() function routed by POST /api/fileThumbnail" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/files/morrowplots-thumb-1.jpg")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      val req = FakeRequest(POST, "/api/fileThumbnail?key=" + secretKey).
        withFileUpload("File", file1, "image/jpg")
      val result = route(req).get

      info("Status=" + status(result))
      status(result) mustEqual OK
      info("contentType=" + contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include("id")
      info("contentAsString" + contentAsString(result))
    }

 "respond to the list() function routed by GET /api/files" in {
      val Some(result) = route(FakeRequest(GET, "/api/files"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("filename")
      contentAsString(result) must include ("id")
      contentAsString(result) must include ("content-type")
      contentAsString(result) must include ("date-created")
      contentAsString(result) must include ("size")
      info("content"+contentAsString(result))
    }

    "respond to the addMetadata(id: UUID) function routed by POST /api/files/:id/metadata" in {
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
          info("id value " + id)

          //link up json file here before fake request.
          val workingDir = System.getProperty("user.dir")
          info("Working Directory: " + workingDir)
          val file1 = new java.io.File(workingDir + "/test/data/files/data-test-general.json")
          if (file1.isFile && file1.exists) {
            Logger.debug("File1 is File:True")
          }
          info("File Pathing " + file1.toString())
          val json_data_from_file_source = Source.fromFile(file1.toString())
          val json_data_from_file_lines = json_data_from_file_source.mkString
          json_data_from_file_source.close()

          // Place file string into a JSON object
          val json_meta: JsValue = Json.parse(json_data_from_file_lines)
          val readableString_meta: String = Json.prettyPrint(json_meta)
          info("Pretty JSON format")
          info(readableString_meta)

          // Send JSON object into RESTful API and read response
          val Some(result_get) = route(FakeRequest(POST, "/api/files/" + id + "/metadata?key=" + secretKey).withJsonBody(json_meta))
          status(result_get) mustEqual OK
          info("Status_Get="+status(result_get))
          status(result_get) mustEqual OK
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("application/json")
          list
        case e: JsError => {
          info("Mapping file model to Json failed")
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }

    "respond to the addUserMetadata(id: UUID) function routed by POST /api/files/:id/usermetadata" in {
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
          info("id value " + id)

          //link up json file here before fake request.
          val workingDir = System.getProperty("user.dir")
          info("Working Directory: " + workingDir)
          val file1 = new java.io.File(workingDir + "/test/data/files/data-test-user.json")
          if (file1.isFile && file1.exists) {
            Logger.debug("File1 is File:True")
          }
          info("File Pathing " + file1.toString())
          val json_data_from_file_source = Source.fromFile(file1.toString())
          val json_data_from_file_lines = json_data_from_file_source.mkString
          json_data_from_file_source.close()

          // Place file string into a JSON object
          val json_meta: JsValue = Json.parse(json_data_from_file_lines)
          val readableString_meta: String = Json.prettyPrint(json_meta)
          info("Pretty JSON format")
          info(readableString_meta)

          // Send JSON object into RESTful API and read response
          val Some(result_get) = route(FakeRequest(POST, "/api/files/" + id + "/usermetadata?key=" + secretKey).withJsonBody(json_meta))
          status(result_get) mustEqual OK
          info("Status_Get="+status(result_get))
          status(result_get) mustEqual OK
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("application/json")
          list
        case e: JsError => {
          info("Mapping file model to Json failed")
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }

    "respond to the get(id: UUID) function routed by GET /api/files/:id/metadata" in {
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
          val Some(result_get) = route(FakeRequest(GET, "/api/files/" + id + "/metadata"))
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

    "respond to the searchFilesGeneralMetadata() function routed by POST /api/files/searchmetadata  " in {

      //link up json file here before fake request.
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/files/data-search-general.json")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      info("File Pathing " + file1.toString())
      val json_data_from_file_source = Source.fromFile(file1.toString())
      val json_data_from_file_lines = json_data_from_file_source.mkString
      json_data_from_file_source.close()

      // Place file string into a JSON object
      val json_meta: JsValue = Json.parse(json_data_from_file_lines)
      val readableString: String = Json.prettyPrint(json_meta)
      info("Pretty JSON format")
      info(readableString)

      // Send JSON object into RESTful API and read response
      val Some(result) = route(FakeRequest(POST, "/api/files/searchmetadata?key=" + secretKey).withJsonBody(json_meta))
      info("Status="+status(result))
      //status(result) mustEqual OK
      info("contentType="+contentType(result))
      //contentType(result) mustEqual Some("application/json")
      info("content"+contentAsString(result))
    }

    "respond to the searchFilesUserMetadata() function routed by POST /api/files/searchmetadata  " in {

      //link up json file here before fake request.
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/files/data-search-user.json")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      info("File Pathing " + file1.toString())
      val json_data_from_file_source = Source.fromFile(file1.toString())
      val json_data_from_file_lines = json_data_from_file_source.mkString
      json_data_from_file_source.close()

      // Place file string into a JSON object
      val json_meta: JsValue = Json.parse(json_data_from_file_lines)
      val readableString: String = Json.prettyPrint(json_meta)
      info("Pretty JSON format")
      info(readableString)

      // Send JSON object into RESTful API and read response
      val Some(result) = route(FakeRequest(POST, "/api/files/searchusermetadata?key=" + secretKey).withJsonBody(json_meta))
      info("Status="+status(result))
      //status(result) mustEqual OK
      info("contentType="+contentType(result))
      //contentType(result) mustEqual Some("application/json")
      info("content"+contentAsString(result))
    }

    "respond to the attachThumbnail(file_id:UUID, thumbnail:UUID) function routed by POST /api/files/:file_id/thumbnails/:thumbnails_id  " in {

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
          val file1 = new java.io.File(workingDir + "/test/data/files/morrowplots-thumb.jpg")
          if (file1.isFile && file1.exists) {
            Logger.debug("File1 is File:True")
          }
          val req = FakeRequest(POST, "/api/fileThumbnail?key=" + secretKey).
            withFileUpload("File", file1, "image/jpg")
          val result = route(req).get

          //val thumbnail = result.toString().split(",")(2)
          //val thumbnail = result.toString()
          info("Status=" + status(result))
          status(result) mustEqual OK
          info("contentType=" + contentType(result))
          contentType(result) mustEqual Some("application/json")
          contentAsString(result) must include("id")
          info("contentAsString" + contentAsString(result))
          val thumbnail = contentAsString(result).split(":")(1).split("\"")(1)

          // After finding specific "id" of file call RESTful API to get JSON information
          info("POST /api/files/" + file_id + "/thumbnails/" + thumbnail)
          val Some(result_get) = route(FakeRequest(POST, "/api/files/" + file_id + "/thumbnails/" + thumbnail + "?key=" + secretKey))

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



// Update License Type
// Add Tag/Remove Tag
// Add Notes

    "respond to the removeFile(id:UUID) function routed by POST /api/files/:id/remove  " in {

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

          // info(list.filter(_.filename contains "logos_ncsa.png").toString().split(",")(2))
          // val id = list.filter(_.filename contains "logos_ncsa.png").toString().split(",")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          info("DELETE /api/files/" + id)
          val Some(result_get) = route(FakeRequest(POST, "/api/files/" + id + "/remove?key=" + secretKey))
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
          // info(list.filter(_.filename contains "morrowplots.jpg").toString().split(",")(2))
          // val id = list.filter(_.filename contains "morrowplots.jpg").toString().split(",")(2)

          info(list.filter(_.filename contains "logos_ncsa.png").toString().split(",")(2))
          val id = list.filter(_.filename contains "logos_ncsa.png").toString().split(",")(2)

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


// Add Preview/Remove Preview
// Datasets containing the file

 }
}