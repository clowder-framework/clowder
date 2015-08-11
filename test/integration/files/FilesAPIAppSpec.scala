package integration

import org.scalatestplus.play.{PlaySpec, _}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, _}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Logger, Play}

import scala.io.Source

/*
 * Integration test for Files API - Router test
 * @author Eugene Roeder
 * 
 */


//@DoNotDiscover
class FilesAPIAppSpec extends PlaySpec with ConfiguredApp with FakeMultipartUpload {

  lazy val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
  lazy val workingDir = System.getProperty("user.dir")

  var morrowPlotFileId: String = ""
  var morrowPlotThumbnailId: String = ""

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
    "respond to the Upload File" in {
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
      morrowPlotFileId = contentAsJson(result).\("id").as[String]
      info("contentAsString" + contentAsString(result))
    }

  "respond to the uploadThumbnail() function routed by POST /api/fileThumbnail" in {
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
      morrowPlotThumbnailId = contentAsJson(result).\("id").as[String]
      info("contentAsString" + contentAsString(result))
    }

   /**
    * This test is no longer enabled since the actual endpoint  is disabled.
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
    */

    "respond to the addMetadata(id: UUID) function routed by POST /api/files/:id/metadata" in {
      //link up json file here before fake request.
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/files/data-test-general.json")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      info("File Pathing " + file1.toString)
      val json_data_from_file_source = Source.fromFile(file1.toString)
      val json_data_from_file_lines = json_data_from_file_source.mkString
      json_data_from_file_source.close()

      // Place file string into a JSON object
      val json_meta: JsValue = Json.parse(json_data_from_file_lines)
      val readableString_meta: String = Json.prettyPrint(json_meta)
      info("Pretty JSON format")
      info(readableString_meta)

      // Send JSON object into RESTful API and read response
      val Some(result_get) = route(FakeRequest(POST, "/api/files/" + morrowPlotFileId + "/metadata?key=" + secretKey).withJsonBody(json_meta))
      status(result_get) mustEqual OK
      info("Status_Get="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
    }

    "respond to the addUserMetadata(id: UUID) function routed by POST /api/files/:id/usermetadata" in {
      //link up json file here before fake request.
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/files/data-test-user.json")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      info("File Pathing " + file1.toString)
      val json_data_from_file_source = Source.fromFile(file1.toString)
      val json_data_from_file_lines = json_data_from_file_source.mkString
      json_data_from_file_source.close()

      // Place file string into a JSON object
      val json_meta: JsValue = Json.parse(json_data_from_file_lines)
      val readableString_meta: String = Json.prettyPrint(json_meta)
      info("Pretty JSON format")
      info(readableString_meta)

      // Send JSON object into RESTful API and read response
      val Some(result_get) = route(FakeRequest(POST, "/api/files/" + morrowPlotFileId + "/usermetadata?key=" + secretKey).withJsonBody(json_meta))
      status(result_get) mustEqual OK
      info("Status_Get="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
    }

    "respond to the get(id: UUID) function routed by GET /api/files/:id/metadata" in {
      // Call RESTful API to get JSON information
      val Some(result_get) = route(FakeRequest(GET, "/api/files/" + morrowPlotFileId + "/metadata"))
      info("Status_Get="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
    }

    "respond to the searchFilesGeneralMetadata() function routed by POST /api/files/searchmetadata  " in {
      //link up json file here before fake request.
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/files/data-search-general.json")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      info("File Pathing " + file1.toString)
      val json_data_from_file_source = Source.fromFile(file1.toString)
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
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/files/data-search-user.json")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      info("File Pathing " + file1.toString)
      val json_data_from_file_source = Source.fromFile(file1.toString)
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
      // After finding specific "id" of file call RESTful API to get JSON information
      info("POST /api/files/" + morrowPlotFileId + "/thumbnails/" + morrowPlotThumbnailId)
      val Some(result_get) = route(FakeRequest(POST, "/api/files/" + morrowPlotFileId + "/thumbnails/" + morrowPlotThumbnailId + "?key=" + secretKey))

      info("Status_Get="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
    }



// Update License Type
// Add Tag/Remove Tag
// Add Notes

    "respond to the removeFile(id:UUID) function routed by POST /api/files/:id/remove  " in {
      // Call RESTful API to get JSON information
      info("DELETE /api/files/" + morrowPlotFileId)
      val Some(result_get) = route(FakeRequest(POST, "/api/files/" + morrowPlotFileId + "/remove?key=" + secretKey))
      info("Status_Get="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
    }

// Add Preview/Remove Preview
// Datasets containing the file

 }
}