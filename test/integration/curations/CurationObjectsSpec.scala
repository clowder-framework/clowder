package integration

import org.scalatestplus.play.{PlaySpec, _}
import org.specs2._

import play.api.http.{HeaderNames, Writeable}
import play.api.libs.Files.TemporaryFile

import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, _}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Logger, Play}

import scala.io.Source

/*
 * Integration test for Curation object API - Router test
 */


//@DoNotDiscover
class CurationObjectsSpec extends PlaySpec with ConfiguredApp with FakeMultipartUpload {

  implicit val user: Option[models.User] = None

  lazy val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
  lazy val workingDir = System.getProperty("user.dir")

  var collection1Id: String = ""
  var collection2Id: String = ""
  var zipFile1Id: String = ""
  var zipFile2Id: String = ""
  var dataset1Id: String = ""
  var dataset2Id: String = ""
  var spaceId:String = ""

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

  case class SpacesSet(id: String, name: String, description: String, created: String)

  implicit val spacesReads: Reads[SpacesSet] = (
    (__ \ "id").read[String] and
      (__ \ "name").read[String] and
      (__ \ "description").read[String] and
      (__ \ "created").read[String]
    )(SpacesSet.apply _)

  implicit val anyContentAsMultipartFormWritable: Writeable[AnyContentAsMultipartFormData] = {
    MultipartFormDataWritable.singleton.map(_.mdf)
  }

  "The CurationObject Controller Spec" must {
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

  "The CurationObject Controller Spec" must {
    "respond to the createDataset() function routed by POST /api/datasets for Dataset 2 Creation" in {
      //upload files
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/collections/dataset-image-1.zip")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }

      val req1 = FakeRequest(POST, "/api/extractions/upload_file?key=" + secretKey).
        withFileUpload("File", file1, "application/x-zip-compressed")
      val result1 = route(req1).get
      contentAsString(result1) must include("id")
      zipFile2Id = contentAsJson(result1).\("id").as[String]

      //create space
      val file2 = new java.io.File(workingDir + "/test/data/spaces/data-test-spaces.json")
      if (file2.isFile && file2.exists) {
        Logger.debug("File1 is File:True")
      }
      val json_data_from_file_source = Source.fromFile(file2.toString())
      val json_data_from_file_lines = json_data_from_file_source.mkString
      val json_tags: JsValue = Json.parse(json_data_from_file_lines)

      val Some(result_get) = route(FakeRequest(POST, "/api/spaces?key=" + secretKey).withJsonBody(json_tags))

      info("Status=" + status(result_get))
      //status(result_get) mustEqual OK
      info("contentType=" + contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      //contentAsString(result_get) must include ("File")
      info("content" + contentAsString(result_get))
      spaceId = contentAsJson(result_get).\("id").as[String]

      //create dataset and add to space
      val name = "Dataset 2 API Test Creation"
      val description = "Part 2 of Dataset API Test Suite"
      //val file_id = ""

      val req2 = FakeRequest(POST, "/api/datasets?key=" + secretKey).
        withJsonBody(Json.toJson(Map("name" -> name, "description" -> description, "file_id" -> zipFile2Id, "space" -> spaceId)))
      val result2 = route(req2).get

      status(result2) mustEqual OK
      contentAsString(result2) must include("id")
      dataset2Id = contentAsJson(result2).\("id").as[String]

      //create curation object
      val creator = "Anonymous"



//
//      val loginRequest = FakeRequest(GET, "/").withCookies(LoginUtil.cookie)
//      info(loginRequest.body.toString)
//
//        val createpage = route(FakeRequest(GET, "/datasets/"+dataset2Id+"/curations/new?key="+secretKey).withCookies(LoginUtil.cookie)).get
//
//        status(createpage) mustEqual OK
//        contentType(createpage) mustEqual Some("text/html")
//        contentAsString(createpage) must include("Create")
////
//
//      info("curation create page    " + contentAsString(createpage))
      //      val data:MultipartFormData[TemporaryFile] = MultipartFormData(
      //          dataParts = Map("name" -> Seq(name), "description" -> Seq(description), "creators" -> Seq(creator)),
      //          files= Seq.empty,
      //          badParts = Seq.empty,
      //          missingFileParts = Seq.empty
      //      )
      //      val test = formatDataParts(data.dataParts)
      //      val singleton = Writeable[MultipartFormData[TemporaryFile]](
      //        transform = {test},
      //        contentType = Some(s"multipart/form-data; boundary=$boundary")
      //      )

      //      val Some(result4) = route(FakeRequest(POST, "/dataset/" + dataset2Id + "/curations/spaces/" + spaceId
      //        + "/submit?key=" + secretKey).withMultipartFormDataBody(singleton))



      val req = FakeRequest(POST, "/datasets/" + dataset2Id + "/curations/spaces/" + spaceId
        + "/submit?key=" + secretKey)
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map("name" -> Seq(name), "description" -> Seq(description), "creators" -> Seq(creator)),
            files = Seq.empty,
            badParts = Seq.empty,
            missingFileParts = Seq.empty
          )
        )
      val result4 = route(req)(anyContentAsMultipartFormWritable).get

      val bodyText: String = contentAsString(result4)

      info("curation return    " + bodyText)
      info("curation return    " + redirectLocation(result4).get)
//      bodyText must include("Dataset")


    }
  }


}