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
 * Integration tests for Context-LD API - Router test
 * @author Eugene Roeder
 * 
 */


//@DoNotDiscover
class ContextLDAPIAppSpec extends PlaySpec with ConfiguredApp with FakeMultipartUpload {

  case class FileName(size: String, datecreated: String, id: String, contenttype: String, filename: String)

  implicit val fileReads: Reads[FileName] = (
    (__ \ "size").read[String] and
    (__ \ "date-created").read[String] and
    (__ \ "id").read[String] and
    (__ \ "content-type").read[String] and
    (__ \ "filename").read[String]
  )(FileName.apply _)



  case class DataSet(description: String, thumbnail: String, id: String, datasetname: String, authorId: String, created: String)

  implicit val datasetReads: Reads[DataSet] = (
    (__ \ "description").read[String] and
    (__ \ "thumbnail").read[String] and
    (__ \ "id").read[String] and
    (__ \ "datasetname").read[String] and
    (__ \ "authorId").read[String] and
    (__ \ "created").read[String]
  )(DataSet.apply _)


  case class CollectionSet(id: String, name: String, description: String, created: String)

  implicit val collectionReads: Reads[CollectionSet] = (
    (__ \ "id").read[String] and
    (__ \ "name").read[String] and
    (__ \ "description").read[String] and
    (__ \ "created").read[String]
  )(CollectionSet.apply _)



  "The Context-LD API Spec" must {
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
 	
  "The Context-LD API Spec" must {
    "respond to the createContext() function routed by POST /api/contexts" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")

      //link up json file here before fake request.
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/contextld/data-test-contextld.json")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      info("File Pathing " + file1.toString())
      val json_data_from_file_source = Source.fromFile(file1.toString())
      val json_data_from_file_lines = json_data_from_file_source.mkString
      json_data_from_file_source.close()

      // Place file string into a JSON object
      val json_context: JsValue = Json.parse(json_data_from_file_lines)
      val readableString_context: String = Json.prettyPrint(json_context)
      info("Pretty JSON format")
      info(readableString_context)          

      //link up json file here before fake request.
      val Some(result_post) = route(FakeRequest(POST, "/api/contexts?key=" + secretKey).withJsonBody(json_context))

      info("Status="+status(result_post))
      status(result_post) mustEqual OK
      info("contentType="+contentType(result_post))
      contentType(result_post) mustEqual Some("application/json")
      //contentAsString(result_get) must include ("File")
      info("content"+contentAsString(result_post))
    }


 "respond to the getContextByName() function routed by GET /api/contexts/:name/context.json" in {
      val Some(result) = route(FakeRequest(GET, "/api/contexts/Person/context.json"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      //contentType(result) mustEqual Some("application/json")
      info("content"+contentAsString(result))
    }



  "respond to the getContextById(id: UUID) and removeContextById(id: UUID) functions routed by GET/DELETE /api/contexts/:id" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")

      //link up json file here before fake request.
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/contextld/data-test-contextld-2.json")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      info("File Pathing " + file1.toString())
      val json_data_from_file_source = Source.fromFile(file1.toString())
      val json_data_from_file_lines = json_data_from_file_source.mkString
      json_data_from_file_source.close()

      // Place file string into a JSON object
      val json_context: JsValue = Json.parse(json_data_from_file_lines)
      val readableString_context: String = Json.prettyPrint(json_context)
      info("Pretty JSON format")
      info(readableString_context)          

      //link up json file here before fake request.
      val Some(result_post) = route(FakeRequest(POST, "/api/contexts?key=" + secretKey).withJsonBody(json_context))

      info("Status="+status(result_post))
      status(result_post) mustEqual OK
      info("contentType="+contentType(result_post))
      contentType(result_post) mustEqual Some("application/json")
      contentAsString(result_post) must include("id")
      info("content"+contentAsString(result_post))

      val json: JsValue = Json.parse(contentAsString(result_post))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)

      val id = readableString.toString().split(":")(1).split("\"")(1)
      info("id value " + id)

      val req2 = FakeRequest(GET, "/api/contexts/" + id + "?key=" + secretKey)
      val result2 = route(req2).get

      info("Status=" + status(result2))
      status(result2) mustEqual OK
      info("contentType=" + contentType(result2))
      contentType(result2) mustEqual Some("application/json")
      info("contentAsString" + contentAsString(result2)) 


      val req3 = FakeRequest(DELETE, "/api/contexts/" + id + "?key=" + secretKey)
      val result3 = route(req3).get

      info("Status=" + status(result3))
      status(result3) mustEqual OK
      info("contentType=" + contentType(result3))
      contentType(result3) mustEqual Some("text/plain")
      info("contentAsString" + contentAsString(result3)) 
    }
 
 } // End Test Suite Bracket
} // End Class Bracket