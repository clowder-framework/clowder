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
import org.scalatest._


import play.api.test._
import org.scalatest._
import org.scalatestplus.play._
import play.api.{Play, Application}

/*
 * Integration test for Datasets API - Router test
 * @author Eugene Roeder
 * 
 */


//@DoNotDiscover
class DatasetsAPIAppSpec extends PlaySpec with OneAppPerSuite with FakeMultipartUpload {
  // val excludedPlugins = List(
  //   "services.RabbitmqPlugin",
  //   "services.VersusPlugin")


  // implicit override lazy val app: FakeApplication = FakeApplication(withoutPlugins = excludedPlugins)

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


  case class DataSet(description: String, thumbnail: String, id: String, datasetname: String, authorId: String, created: String)

  implicit val datasetReads: Reads[DataSet] = (
    (__ \ "description").read[String] and
    (__ \ "thumbnail").read[String] and
    (__ \ "id").read[String] and
    (__ \ "datasetname").read[String] and
    (__ \ "authorId").read[String] and
    (__ \ "created").read[String]
  )(DataSet.apply _)

  
 "The OneAppPerSuite for Datasets API Router test" must {
    "respond to the createDataset() function routed by POST /api/datasets" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/datasets/dataset-image.zip")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }

      val req = FakeRequest(POST, "/api/extractions/upload_file?key=" + secretKey).
        withFileUpload("File", file1, "application/x-zip-compressed")
      val result = route(req).get


      val Some(result1) = route(FakeRequest(GET, "/api/files"))
      info("Status="+status(result1))
      status(result1) mustEqual OK
      info("contentType="+contentType(result1))
      contentType(result1) mustEqual Some("application/json")
      contentAsString(result1) must include ("filename")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result1))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[FileName]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[FileName], _) => (list)
          info("Mapping file model to Json worked")
          info("Number of files in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.filename contains "dataset-image.zip").toString().split(",")(2))
          val file_id = list.filter(_.filename contains "dataset-image.zip").toString().split(",")(2)
          info("id value " + file_id)

          val name = "Dataset API Test Creation"
          val description = "Part of Dataset API Test Suite"
          //val file_id = ""

          val req = FakeRequest(POST, "/api/datasets?key=" + secretKey).
              withJsonBody(Json.toJson(Map("name" -> name, "description" -> description, "file_id" -> file_id)))
          val result3 = route(req).get

          info("Status=" + status(result3))
          status(result3) mustEqual OK
          info("contentType=" + contentType(result3))
          contentType(result3) mustEqual Some("application/json")
          contentAsString(result3) must include("id")
          info("contentAsString" + contentAsString(result3))
        }
    }

 "respond to the list() function routed by GET /api/datasets" in {
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("description")
      contentAsString(result) must include ("id")
      contentAsString(result) must include ("datasetname")
      contentAsString(result) must include ("created")
      contentAsString(result) must include ("authorId")
      contentAsString(result) must include ("thumbnail")
      info("content"+contentAsString(result))
    }


 "respond to the datasetFilesList(id:UUID) function routed by GET /api/datasets/:id  " in {

      //link up json file here before fake request.
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          info("GET /api/datasets/" + id)
          val Some(result_get) = route(FakeRequest(GET, "/api/datasets/" + id + "?key=" + secretKey))
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


 "respond to the datasetFilesList(id:UUID) function routed by GET /api/datasets/:id/listFiles  " in {

      //link up json file here before fake request.
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          info("GET /api/datasets/" + id + "/listFiles")
          val Some(result_get) = route(FakeRequest(GET, "/api/datasets/" + id + "/listFiles?key=" + secretKey))
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

    "respond to the updateLicense(id: UUID) function routed by POST /api/datasets/:id/license" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

          //link up json file here before fake request.
          val req = FakeRequest(POST, "/api/datasets/" + id + "/license?key=" + secretKey).
              withJsonBody(Json.toJson(Map("licenseType" -> "NCSA Open Source", "rightsHolder" -> "API Test Suite", "licenseText" -> "by", "licenseUrl" -> "https://medici.ncsa.illinois.edu", "allowDownload" -> "True")))
          val result_get = route(req).get

          status(result_get) mustEqual OK
          info("Status_Get="+status(result_get))
          status(result_get) mustEqual OK
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("application/json")
          info("content"+contentAsString(result_get))
          list
        case e: JsError => {
          info("Mapping dataset model to Json failed")
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }

    "respond to the comment(id: UUID) function routed by POST /api/datasets/:id/comment" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))      
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

          //link up json file here before fake request.
          val req = FakeRequest(POST, "/api/datasets/" + id + "/comment?key=" + secretKey).
              withJsonBody(Json.toJson(Map("text" -> "API Test Suite Comment")))
          val result_get = route(req).get

          status(result_get) mustEqual OK
          info("Status_Get="+status(result_get))
          status(result_get) mustEqual OK
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("text/plain")
          info("content"+contentAsString(result_get))
          list
        case e: JsError => {
          info("Mapping dataset model to Json failed")
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }

    "respond to the addTags(id: UUID) function routed by POST /api/datasets/:id/tags" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

         //link up json file here before fake request.
          val workingDir = System.getProperty("user.dir")
          info("Working Directory: " + workingDir)
          val file1 = new java.io.File(workingDir + "/test/data/datasets/dataset-test-tags.json")
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
          val Some(result_get) = route(FakeRequest(POST, "/api/datasets/" + id + "/tags?key=" + secretKey).withJsonBody(json_tags))
             // withJsonBody(Json.toJson(Map("tags" -> List("Dataset", "Test Suite", "Medici"), "extractor_id" -> "ncsa.cv.face")))
          ///val result_get = route(req).get

          status(result_get) mustEqual OK
          info("Status_Get="+status(result_get))
          status(result_get) mustEqual OK
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("application/json")
          info("content"+contentAsString(result_get))
          list
        case e: JsError => {
          info("Mapping dataset model to Json failed")
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }

    "respond to the removeTags(id: UUID) function routed by POST /api/datasets/:id/tags/remove" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

          //link up json file here before fake request.
          val workingDir = System.getProperty("user.dir")
          info("Working Directory: " + workingDir)
          val file1 = new java.io.File(workingDir + "/test/data/datasets/dataset-test-tags-remove.json")
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
          val Some(result_get) = route(FakeRequest(POST, "/api/datasets/" + id + "/tags/remove?key=" + secretKey).withJsonBody(json_tags))

          status(result_get) mustEqual OK
          info("Status_Get="+status(result_get))
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("application/json")
          info("content"+contentAsString(result_get))
          list
        case e: JsError => {
          info("Mapping dataset model to Json failed")
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }


"respond to the getTags(id:UUID) function routed by GET /api/datasets/:id/tags  " in {

      //link up json file here before fake request.
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          info("GET /api/datasets/" + id + "/tags")
          val Some(result_get) = route(FakeRequest(GET, "/api/datasets/" + id + "/tags?key=" + secretKey))
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


    "respond to the addTags(id: UUID) function routed by POST /api/datasets/:id/tags round two" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

         //link up json file here before fake request.
          val workingDir = System.getProperty("user.dir")
          info("Working Directory: " + workingDir)
          val file1 = new java.io.File(workingDir + "/test/data/datasets/dataset-test-tags-round-two.json")
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
          val Some(result_get) = route(FakeRequest(POST, "/api/datasets/" + id + "/tags?key=" + secretKey).withJsonBody(json_tags))
             // withJsonBody(Json.toJson(Map("tags" -> List("Dataset", "Test Suite", "Medici"), "extractor_id" -> "ncsa.cv.face")))
          ///val result_get = route(req).get

          status(result_get) mustEqual OK
          info("Status_Get="+status(result_get))
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("application/json")
          info("content"+contentAsString(result_get))
          list
        case e: JsError => {
          info("Mapping dataset model to Json failed")
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }

    "respond to the removeTag(id: UUID) function routed by POST /api/datasets/:id/removeTag" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

          //link up json file here before fake request.
          val workingDir = System.getProperty("user.dir")
          info("Working Directory: " + workingDir)
          val file1 = new java.io.File(workingDir + "/test/data/datasets/dataset-test-tags-remove-one.json")
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
          val Some(result_get) = route(FakeRequest(POST, "/api/datasets/" + id + "/removeTag?key=" + secretKey).withJsonBody(json_tags))

          status(result_get) mustEqual OK
          info("Status_Get="+status(result_get))
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("application/json")
          info("content"+contentAsString(result_get))
          list
        case e: JsError => {
          info("Mapping dataset model to Json failed")
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }

    // "respond to the removeTags(id: UUID) function routed by DELETE /api/datasets/:id/tags" in {
    //   val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
    //   val Some(result) = route(FakeRequest(GET, "/api/datasets"))
    //   info("Status="+status(result))
    //   status(result) mustEqual OK
    //   info("contentType="+contentType(result))
    //   contentType(result) mustEqual Some("application/json")
    //   contentAsString(result) must include ("datasetname")
    //   info("content"+contentAsString(result))
    //   val json: JsValue = Json.parse(contentAsString(result))
    //   val readableString: String = Json.prettyPrint(json)
    //   info("Pretty JSON format")
    //   info(readableString)
    //   val nameResult = json.validate[List[DataSet]]
    //   val fileInfo = nameResult match {
    //     case JsSuccess(list : List[DataSet], _) => (list)
    //       info("Mapping dataset model to Json worked")
    //       info("Number of datasets in System " + list.length.toString())
    //       info(list.toString())
    //       info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
    //       val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

    //       //link up json file here before fake request.
    //       val workingDir = System.getProperty("user.dir")
    //       info("Working Directory: " + workingDir)
    //       val file1 = new java.io.File(workingDir + "/test/data/datasets/dataset-test-tags-remove-round-two.json")
    //       if (file1.isFile && file1.exists) {
    //         Logger.debug("File1 is File:True")
    //       }
    //       info("File Pathing " + file1.toString())
    //       val json_data_from_file_source = Source.fromFile(file1.toString())
    //       val json_data_from_file_lines = json_data_from_file_source.mkString
    //       json_data_from_file_source.close()

    //       // Place file string into a JSON object
    //       val json_tags: JsValue = Json.parse(json_data_from_file_lines)
    //       val readableString_tags: String = Json.prettyPrint(json_tags)
    //       info("Pretty JSON format")
    //       info(readableString_tags)          

    //       //link up json file here before fake request.
    //       val Some(result_get) = route(FakeRequest(DELETE, "/api/datasets/" + id + "/tags?key=" + secretKey))//.withJsonBody(json_tags))
          
    //       info("content"+contentAsString(result_get))
    //       status(result_get) mustEqual OK
    //       info("Status_Get="+status(result_get))
    //       info("contentType_Get="+contentType(result_get))
    //       contentType(result_get) mustEqual Some("application/json")
    //       list
    //     case e: JsError => {
    //       info("Mapping dataset model to Json failed")
    //       info("Errors: " + JsError.toFlatJson(e).toString())
    //     }
    //   }
    // }

// "respond to the removeAllTags(id:UUID) function routed by POST /api/datasets/:id/tags/remove_all  " in {

//       //link up json file here before fake request.
//       val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
//       val Some(result) = route(FakeRequest(GET, "/api/datasets"))
//       info("Status="+status(result))
//       status(result) mustEqual OK
//       info("contentType="+contentType(result))
//       contentType(result) mustEqual Some("application/json")
//       contentAsString(result) must include ("datasetname")
//       info("content"+contentAsString(result))
//       val json: JsValue = Json.parse(contentAsString(result))
//       val readableString: String = Json.prettyPrint(json)
//       info("Pretty JSON format")
//       info(readableString)
//       val nameResult = json.validate[List[DataSet]]
//       val fileInfo = nameResult match {
//         case JsSuccess(list : List[DataSet], _) => (list)
//           info("Mapping dataset model to Json worked")
//           info("Number of datasets in System " + list.length.toString())
//           info(list.toString())
//           info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
//           val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

//           // After finding specific "id" of file call RESTful API to get JSON information
//           info("GET /api/datasets/" + id + "/tags/remove_all")
//           val Some(result_get) = route(FakeRequest(POST, "/api/datasets/" + id + "/tags/remove_all?key=" + secretKey))
//           info("content"+contentAsString(result_get))
//           info("Status_Get="+status(result_get))
//           status(result_get) mustEqual OK
//           info("contentType_Get="+contentType(result_get))
//           contentType(result_get) mustEqual Some("application/json")
//           val json: JsValue = Json.parse(contentAsString(result_get))
//           val readableString: String = Json.prettyPrint(json)
//           info("Pretty JSON format")
//           info(readableString)
//         case e: JsError => {
//           info("Errors: " + JsError.toFlatJson(e).toString())
//         }
//       }
//     }



    "respond to the addMetadata(id: UUID) function routed by POST /api/datasets/:id/metadata" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

          //link up json file here before fake request.
          val workingDir = System.getProperty("user.dir")
          info("Working Directory: " + workingDir)
          val file1 = new java.io.File(workingDir + "/test/data/datasets/dataset-test-general.json")
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
          val Some(result_get) = route(FakeRequest(POST, "/api/datasets/" + id + "/metadata?key=" + secretKey).withJsonBody(json_meta))
          status(result_get) mustEqual OK
          info("Status_Get="+status(result_get))
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("application/json")
          list
        case e: JsError => {
          info("Mapping dataset model to Json failed")
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }

    "respond to the addUserMetadata(id: UUID) function routed by POST /api/datasets/:id/usermetadata" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

          //link up json file here before fake request.
          val workingDir = System.getProperty("user.dir")
          info("Working Directory: " + workingDir)
          val file1 = new java.io.File(workingDir + "/test/data/datasets/dataset-test-user.json")
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
          val Some(result_get) = route(FakeRequest(POST, "/api/datasets/" + id + "/usermetadata?key=" + secretKey).withJsonBody(json_meta))
          status(result_get) mustEqual OK
          info("Status_Get="+status(result_get))
          status(result_get) mustEqual OK
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("application/json")
          list
        case e: JsError => {
          info("Mapping dataset model to Json failed")
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }

    "respond to the getUserMetadataJSON(id: UUID) function routed by GET /api/datasets/:id/usermetadata" in {
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          val Some(result_get) = route(FakeRequest(GET, "/api/datasets/" + id + "/usermetadatajson"))
          info("Status_Get="+status(result_get))
          status(result_get) mustEqual OK
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("text/plain")
          val json: JsValue = Json.parse(contentAsString(result_get))
          val readableString: String = Json.prettyPrint(json)
          info("Pretty JSON format")
          info(readableString)
        case e: JsError => {
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }

    "respond to the getTechnicalMetadataJSON(id: UUID) function routed by GET /api/datasets/:id/technicalmetadatajson" in {
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          val Some(result_get) = route(FakeRequest(GET, "/api/datasets/" + id + "/technicalmetadatajson"))
          info("Status_Get="+status(result_get))
          status(result_get) mustEqual OK
          info("contentType_Get="+contentType(result_get))
          contentType(result_get) mustEqual Some("text/plain")
          val json: JsValue = Json.parse(contentAsString(result_get))
          val readableString: String = Json.prettyPrint(json)
          info("Pretty JSON format")
          info(readableString)
        case e: JsError => {
          info("Errors: " + JsError.toFlatJson(e).toString())
        }
      }
    }

    "respond to the searchDatasetsGeneralMetadata() function routed by POST /api/datasets/searchmetadata  " in {

      //link up json file here before fake request.
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/datasets/dataset-search-general.json")
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
      val Some(result) = route(FakeRequest(POST, "/api/datasets/searchmetadata?key=" + secretKey).withJsonBody(json_meta))
      info("Status="+status(result))
      //status(result) mustEqual OK
      info("contentType="+contentType(result))
      //contentType(result) mustEqual Some("application/json")
      info("content"+contentAsString(result))
    }

    "respond to the searchDatasetsUserMetadata() function routed by POST /api/datasets/searchmetadata  " in {

      //link up json file here before fake request.
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/datasets/dataset-search-user.json")
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
      val Some(result) = route(FakeRequest(POST, "/api/datasets/searchusermetadata?key=" + secretKey).withJsonBody(json_meta))
      info("Status="+status(result))
      //status(result) mustEqual OK
      info("contentType="+contentType(result))
      //contentType(result) mustEqual Some("application/json")
      info("content"+contentAsString(result))
    }

// // Add Tag/Remove Tag
// // Add Notes

    // "respond to the deleteDataset(id:UUID) function routed by POST /api/datasets/:datasetId/remove  " in {

    //   //link up json file here before fake request.
    //   val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
    //   val Some(result) = route(FakeRequest(GET, "/api/datasets"))
    //   info("Status="+status(result))
    //   status(result) mustEqual OK
    //   info("contentType="+contentType(result))
    //   contentType(result) mustEqual Some("application/json")
    //   contentAsString(result) must include ("datasetname")
    //   info("content"+contentAsString(result))
    //   val json: JsValue = Json.parse(contentAsString(result))
    //   val readableString: String = Json.prettyPrint(json)
    //   info("Pretty JSON format")
    //   info(readableString)
    //   val nameResult = json.validate[List[DataSet]]
    //   val fileInfo = nameResult match {
    //     case JsSuccess(list : List[DataSet], _) => (list)
    //       info("Mapping dataset model to Json worked")
    //       info("Number of datasets in System " + list.length.toString())
    //       info(list.toString())
    //       info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
    //       val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

    //       // After finding specific "id" of file call RESTful API to get JSON information
    //       info("DELETE /api/datasets/" + id)
    //       val Some(result_get) = route(FakeRequest(POST, "/api/datasets/" + id + "/remove?key=" + secretKey))
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

    "respond to the deleteDataset(id:UUID) function routed by DELETE /api/datasets/:id  " in {

      //link up json file here before fake request.
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("datasetname")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2))
          val id = list.filter(_.datasetname contains "Dataset API Test Creation").toString().split(",")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          info("DELETE /api/datasets/" + id)
          val Some(result_get) = route(FakeRequest(DELETE, "/api/datasets/" + id + "?key=" + secretKey))
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