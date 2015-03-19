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
 * Integration tests for Spaces API - Router test
 * @author Eugene Roeder
 * 
 */


//@DoNotDiscover
class SpacesAPIAppSpec extends PlaySpec with ConfiguredApp with FakeMultipartUpload {

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



  "The Space API Spec must" must {
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
 	
 "The Space API Spec must" must {
    "respond to the createSpace() function routed by POST /api/spaces" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")

      //link up json file here before fake request.
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/space/data-test-spaces.json")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      info("File Pathing " + file1.toString())
      val json_data_from_file_source = Source.fromFile(file1.toString())
      val json_data_from_file_lines = json_data_from_file_source.mkString
      json_data_from_file_source.close()

      // Place file string into a JSON object
      val json_tags: JsValue = Json.parse(json_data_from_file_lines)
      val readableString_tags: String = Json.prettyPrint(json_tags)
      info("Pretty JSON format")
      info(readableString_tags)          

      //link up json file here before fake request.
      val Some(result_get) = route(FakeRequest(POST, "/api/spaces?key=" + secretKey).withJsonBody(json_tags))

      info("Status="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      //contentAsString(result_get) must include ("File")
      info("content"+contentAsString(result_get))
    }


 "respond to the list() function routed by GET /api/spaces" in {
      val Some(result) = route(FakeRequest(GET, "/api/spaces"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      //contentAsString(result) must include ("File")
      info("content"+contentAsString(result))
    }



    // "respond to the removeSpace(id:UUID) function routed by DELETE /api/spaces/:id  " in {
    //   val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
    //   val Some(result) = route(FakeRequest(GET, "/api/spaces"))
    //   info("Status="+status(result))
    //   status(result) mustEqual OK
    //   info("contentType="+contentType(result))
    //   contentType(result) mustEqual Some("application/json")
    //   contentAsString(result) must include ("name")
    //   info("content"+contentAsString(result))
    //   val json: JsValue = Json.parse(contentAsString(result))
    //   val readableString: String = Json.prettyPrint(json)
    //   info("Pretty JSON format")
    //   info(readableString)
    //   val nameResult = json.validate[List[CollectionSet]]
    //   val fileInfo = nameResult match {
    //     case JsSuccess(list : List[CollectionSet], _) => list
    //       info("Mapping collections model to Json worked")
    //       info("Number of collections in System " + list.length.toString())
    //       info(list.toString())


    //       info(list.filter(_.name contains "Spaces").toString().split(",")(0).split("\\(")(2))
    //       val id = list.filter(_.name contains "Spaces").toString().split(",")(0).split("\\(")(2)

    //       // After finding specific "id" of file call RESTful API to get JSON information
    //       info("POST /api/collections/" + coll_id + "/remove")
    //       val Some(result_get) = route(FakeRequest(DELETE, "/api/spaces/" + id + "?key=" + secretKey))
    //       info("Status_Get="+status(result_get))
    //       status(result_get) mustEqual OK
    //       info("contentType_Get="+contentType(result_get))
    //       contentType(result_get) mustEqual Some("application/json")
    //       val json: JsValue = Json.parse(contentAsString(result_get))
    //       val readableString: String = Json.prettyPrint(json)
    //       info("Pretty JSON format")
    //       info(readableString)
    //     case e: JsError => {
    //       info("Errors: " + JsError.toFlatJson(e).toString())
    //     }
    //   }
    // }

 } // End Test Suite Bracket
} // End Class Bracket