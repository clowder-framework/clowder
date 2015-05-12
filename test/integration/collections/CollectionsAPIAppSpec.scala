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
 * Integration tests for Collections API - Router test
 * @author Eugene Roeder
 * 
 */


//@DoNotDiscover
class CollectionsAPIAppSpec extends PlaySpec with ConfiguredApp with FakeMultipartUpload {

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



  "The Collections API Spec" must {
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
 	
 "The Collections API Spec" must {
    "respond to the createCollection() function routed by POST /api/collections for Collection 1" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")

      //link up json file here before fake request.
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/collections/data-test-collection.json")
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
      val Some(result_get) = route(FakeRequest(POST, "/api/collections?key=" + secretKey).withJsonBody(json_tags))

      info("Status="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      //contentAsString(result_get) must include ("File")
      info("content"+contentAsString(result_get))
    }


    "respond to the createCollection() function routed by POST /api/collections for Collection 2" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")

      //link up json file here before fake request.
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/collections/data-test-collection-1.json")
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
      val Some(result_get) = route(FakeRequest(POST, "/api/collections?key=" + secretKey).withJsonBody(json_tags))

      info("Status="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      //contentAsString(result_get) must include ("File")
      info("content"+contentAsString(result_get))
    }

    "respond to the createDataset() function routed by POST /api/datasets for Dataset 1 Creation" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/collections/dataset-image.zip")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }

      val req = FakeRequest(POST, "/api/extractions/upload_file?key=" + secretKey).
        withFileUpload("File", file1, "application/x-zip-compressed")
      val result = route(req).get
      contentAsString(result) must include ("id")


      val Some(result1) = route(FakeRequest(GET, "/api/files"))
      info("Status="+status(result1))
      status(result1) mustEqual OK
      info("contentType="+contentType(result1))
      contentType(result1) mustEqual Some("application/json")
      contentAsString(result1) must include ("filename")
      info("content"+contentAsString(result))
      info("content1 "+contentAsString(result1))
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

          val name = "Dataset 1 API Test Creation"
          val description = "Part 1 of Dataset API Test Suite"

          val req = FakeRequest(POST, "/api/datasets?key=" + secretKey).
              withJsonBody(Json.toJson(Map("name" -> name, "description" -> description, "file_id" -> file_id, "space" -> "default")))
          val result3 = route(req).get

          info("Status=" + status(result3))
          status(result3) mustEqual OK
          info("contentType=" + contentType(result3))
          contentType(result3) mustEqual Some("application/json")
          contentAsString(result3) must include("id")
          info("contentAsString" + contentAsString(result3))
      }
    }

    "respond to the createDataset() function routed by POST /api/datasets for Dataset 2 Creation" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/collections/dataset-image-1.zip")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }

      val req = FakeRequest(POST, "/api/extractions/upload_file?key=" + secretKey).
        withFileUpload("File", file1, "application/x-zip-compressed")
      val result = route(req).get
      contentAsString(result) must include ("id")


      val Some(result1) = route(FakeRequest(GET, "/api/files"))
      info("Status="+status(result1))
      status(result1) mustEqual OK
      info("contentType="+contentType(result1))
      contentType(result1) mustEqual Some("application/json")
      contentAsString(result1) must include ("filename")
      info("content"+contentAsString(result))
      info("content1"+contentAsString(result1))
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
          info(list.filter(_.filename contains "dataset-image-1.zip").toString().split(",")(2))
          val file_id = list.filter(_.filename contains "dataset-image-1.zip").toString().split(",")(2)
          info("id value " + file_id)

          val name = "Dataset 2 API Test Creation"
          val description = "Part 2 of Dataset API Test Suite"
          //val file_id = ""

          val req = FakeRequest(POST, "/api/datasets?key=" + secretKey).
              withJsonBody(Json.toJson(Map("name" -> name, "description" -> description, "file_id" -> file_id, "space" -> "default")))
          val result3 = route(req).get

          info("Status=" + status(result3))
          status(result3) mustEqual OK
          info("contentType=" + contentType(result3))
          contentType(result3) mustEqual Some("application/json")
          contentAsString(result3) must include("id")
          info("contentAsString" + contentAsString(result3))
      }
    }

    "respond to the attachDataset(coll_id:UUID, ds_id:UUID) function routed by POST /api/collections/:coll_id/datasets/:ds_id for dataset 1" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result1) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result1))
      status(result1) mustEqual OK
      info("contentType="+contentType(result1))
      contentType(result1) mustEqual Some("application/json")
      contentAsString(result1) must include ("datasetname")
      info("content"+contentAsString(result1))
      val json: JsValue = Json.parse(contentAsString(result1))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset 1").toString().split(",")(2))
          val dataset_id = list.filter(_.datasetname contains "Dataset 1").toString().split(",")(2)
          info("dataset id value " + dataset_id)

          val Some(result2) = route(FakeRequest(GET, "/api/collections"))
          info("Status="+status(result2))
          status(result2) mustEqual OK
          info("contentType="+contentType(result2))
          contentType(result2) mustEqual Some("application/json")
          contentAsString(result2) must include ("name")
          info("content"+contentAsString(result2))
          val json: JsValue = Json.parse(contentAsString(result2))
          val readableString: String = Json.prettyPrint(json)
          info("Pretty JSON format")
          info(readableString)
          val nameResult = json.validate[List[CollectionSet]]
          val fileInfo = nameResult match {
            case JsSuccess(list : List[CollectionSet], _) => (list)
              info("Mapping collection model to Json worked")
              info("Number of collection in System " + list.length.toString())
              info(list.toString())
              info(list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2))

              val coll_id = list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2)
              info("collection id value " + coll_id)


              info("/api/collections/" + coll_id + "/datasets/" + dataset_id + "?key=" + secretKey)
              val req = FakeRequest(POST, "/api/collections/" + coll_id + "/datasets/" + dataset_id + "?key=" + secretKey)
              //.withJsonBody(Json.toJson(Map("name" -> name, "description" -> description, "file_id" -> file_id)))
              val result3 = route(req).get

              info("Status=" + status(result3))
              status(result3) mustEqual OK
              info("contentType=" + contentType(result3))
              contentType(result3) mustEqual Some("application/json")
              //contentAsString(result3) must include("id")
              info("contentAsString" + contentAsString(result3))
          }
      }
    }

    "respond to the listInCollection(coll_id: UUID) function routed by GET /api/collections/:coll_id/getDatasets" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/collections"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("name")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[CollectionSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[CollectionSet], _) => list
          info("Mapping collection model to Json worked")
          info("Number of collection in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2))
          val coll_id = list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          val Some(result_get) = route(FakeRequest(GET, "/api/collections/" + coll_id + "/getDatasets?key=" + secretKey))
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

    "respond to the listOutsideCollection(coll_id: UUID) function routed by GET /api/datasets/listOutsideCollection/:coll_id" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/collections"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("name")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[CollectionSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[CollectionSet], _) => list
          info("Mapping collection model to Json worked")
          info("Number of collection in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2))
          val coll_id = list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          val Some(result_get) = route(FakeRequest(GET, "/api/datasets/listOutsideCollection/" + coll_id + "?key=" + secretKey))
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


    "respond to the attachDataset(coll_id:UUID, ds_id:UUID) function routed by POST /api/collections/:coll_id/datasets/:ds_id for dataset 2" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")

      val Some(result1) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result1))
      status(result1) mustEqual OK
      info("contentType="+contentType(result1))
      contentType(result1) mustEqual Some("application/json")
      contentAsString(result1) must include ("datasetname")
      info("content"+contentAsString(result1))
      val json: JsValue = Json.parse(contentAsString(result1))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset 2").toString().split(",")(2))
          val dataset_id = list.filter(_.datasetname contains "Dataset 2").toString().split(",")(2)
          info("dataset id value " + dataset_id)

          val Some(result2) = route(FakeRequest(GET, "/api/collections"))
          info("Status="+status(result2))
          status(result2) mustEqual OK
          info("contentType="+contentType(result2))
          contentType(result2) mustEqual Some("application/json")
          contentAsString(result2) must include ("name")
          info("content"+contentAsString(result2))
          val json: JsValue = Json.parse(contentAsString(result2))
          val readableString: String = Json.prettyPrint(json)
          info("Pretty JSON format")
          info(readableString)
          val nameResult = json.validate[List[CollectionSet]]
          val fileInfo = nameResult match {
            case JsSuccess(list : List[CollectionSet], _) => (list)
              info("Mapping collection model to Json worked")
              info("Number of collection in System " + list.length.toString())
              info(list.toString())
              info(list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2))

              val coll_id = list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2)
              info("collection id value " + coll_id)


              info("/api/collections/" + coll_id + "/datasets/" + dataset_id + "?key=" + secretKey)
              val req = FakeRequest(POST, "/api/collections/" + coll_id + "/datasets/" + dataset_id + "?key=" + secretKey)
              //.withJsonBody(Json.toJson(Map("name" -> name, "description" -> description, "file_id" -> file_id)))
              val result3 = route(req).get

              info("Status=" + status(result3))
              status(result3) mustEqual OK
              info("contentType=" + contentType(result3))
              contentType(result3) mustEqual Some("application/json")
              //contentAsString(result3) must include("id")
              info("contentAsString" + contentAsString(result3))
          }
      }
    }

    "respond to the removeDataset(coll_id:UUID, ds_id:UUID, ignoreNotFound) function routed by DELETE /api/collections/:coll_id/datasets/:ds_id for dataset 2" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result1) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result1))
      status(result1) mustEqual OK
      info("contentType="+contentType(result1))
      contentType(result1) mustEqual Some("application/json")
      contentAsString(result1) must include ("datasetname")
      info("content"+contentAsString(result1))
      val json: JsValue = Json.parse(contentAsString(result1))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset 2").toString().split(",")(2))
          val dataset_id = list.filter(_.datasetname contains "Dataset 2").toString().split(",")(2)
          info("dataset id value " + dataset_id)

          val Some(result2) = route(FakeRequest(GET, "/api/collections"))
          info("Status="+status(result2))
          status(result2) mustEqual OK
          info("contentType="+contentType(result2))
          contentType(result2) mustEqual Some("application/json")
          //contentAsString(result2) must include ("name")
          info("content"+contentAsString(result2))
          val json: JsValue = Json.parse(contentAsString(result2))
          val readableString: String = Json.prettyPrint(json)
          info("Pretty JSON format")
          info(readableString)
          val nameResult = json.validate[List[CollectionSet]]
          val fileInfo = nameResult match {
            case JsSuccess(list : List[CollectionSet], _) => (list)
              info("Mapping collection model to Json worked")
              info("Number of collection in System " + list.length.toString())
              info(list.toString())
              info(list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2))
              val coll_id = list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2)
              info("collection id value " + coll_id)

              info("/api/collections/" + coll_id + "/datasets/" + dataset_id + "?key=" + secretKey)
              val req = FakeRequest(DELETE, "/api/collections/" + coll_id + "/datasets/" + dataset_id + "?key=" + secretKey)
              val result3 = route(req).get

              info("Status=" + status(result3))
              status(result3) mustEqual OK
              info("contentType=" + contentType(result3))
              contentType(result3) mustEqual Some("application/json")
              //contentAsString(result3) must include("id")
              info("contentAsString" + contentAsString(result3))
          }
      }
    }

    "respond to the removeDataset(coll_id:UUID, ds_id:UUID, ignoreNoteFound) function routed by POST /api/collections/:coll_id/datasets/:ds_id/:ignoreNotFound for dataset 2" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result1) = route(FakeRequest(GET, "/api/datasets"))
      info("Status="+status(result1))
      status(result1) mustEqual OK
      info("contentType="+contentType(result1))
      contentType(result1) mustEqual Some("application/json")
      contentAsString(result1) must include ("datasetname")
      info("content"+contentAsString(result1))
      val json: JsValue = Json.parse(contentAsString(result1))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[DataSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[DataSet], _) => (list)
          info("Mapping dataset model to Json worked")
          info("Number of datasets in System " + list.length.toString())
          info(list.toString())
          info(list.filter(_.datasetname contains "Dataset 1").toString().split(",")(2))
          val dataset_id = list.filter(_.datasetname contains "Dataset 1").toString().split(",")(2)
          info("dataset id value " + dataset_id)

          val Some(result2) = route(FakeRequest(GET, "/api/collections"))
          info("Status="+status(result2))
          status(result2) mustEqual OK
          info("contentType="+contentType(result2))
          contentType(result2) mustEqual Some("application/json")
          //contentAsString(result2) must include ("name")
          info("content"+contentAsString(result2))
          val json: JsValue = Json.parse(contentAsString(result2))
          val readableString: String = Json.prettyPrint(json)
          info("Pretty JSON format")
          info(readableString)
          val nameResult = json.validate[List[CollectionSet]]
          val fileInfo = nameResult match {
            case JsSuccess(list : List[CollectionSet], _) => (list)
              info("Mapping collection model to Json worked")
              info("Number of collection in System " + list.length.toString())
              info(list.toString())
              info(list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2))
              val coll_id = list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2)
              info("collection id value " + coll_id)

              info("/api/collections/" + coll_id + "/datasets/" + dataset_id + "/true?key=" + secretKey)
              val req = FakeRequest(POST, "/api/collections/" + coll_id + "/datasetsRemove/" + dataset_id + "/true?key=" + secretKey)
              //.withJsonBody(Json.toJson(Map("name" -> name, "description" -> description, "file_id" -> file_id)))
              val result3 = route(req).get

              info("Status=" + status(result3))
              status(result3) mustEqual OK
              info("contentType=" + contentType(result3))
              contentType(result3) mustEqual Some("application/json")
              //contentAsString(result3) must include("id")
              info("contentAsString" + contentAsString(result3))
          }
      }
    }


 "respond to the listCollections() function routed by GET /api/collections" in {
      val Some(result) = route(FakeRequest(GET, "/api/collections"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      //contentAsString(result) must include ("File")
      info("content"+contentAsString(result))
    }

  "respond to the listCollections() function routed by GET /api/collections/list" in {
      val Some(result) = route(FakeRequest(GET, "/api/collections/list"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      //contentAsString(result) must include ("File")
      info("content"+contentAsString(result))
    }

    "respond to the removeCollection(coll_id:UUID) function routed by POST /api/collections/:coll_id/remove  " in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/collections"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("name")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[CollectionSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[CollectionSet], _) => list
          info("Mapping collections model to Json worked")
          info("Number of collections in System " + list.length.toString())
          info(list.toString())


          info(list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2))
          val coll_id = list.filter(_.name contains "Collection 1").toString().split(",")(0).split("\\(")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          info("POST /api/collections/" + coll_id + "/remove")
          val Some(result_get) = route(FakeRequest(POST, "/api/collections/" + coll_id + "/remove?key=" + secretKey))
          //val Some(result_get) = route(FakeRequest(DELETE, "/api/collections/54ff8b41eb1f14ebbe00e5d2?key=" + secretKey))
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

    "respond to the removeCollection(coll_id:UUID) function routed by DELETE /api/collections/:id  " in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val Some(result) = route(FakeRequest(GET, "/api/collections"))
      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include ("name")
      info("content"+contentAsString(result))
      val json: JsValue = Json.parse(contentAsString(result))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      val nameResult = json.validate[List[CollectionSet]]
      val fileInfo = nameResult match {
        case JsSuccess(list : List[CollectionSet], _) => list
          info("Mapping collections model to Json worked")
          info("Number of collections in System " + list.length.toString())
          info(list.toString())


          info(list.filter(_.name contains "Collection 2").toString().split(",")(0).split("\\(")(2))
          val coll_id = list.filter(_.name contains "Collection 2").toString().split(",")(0).split("\\(")(2)

          // After finding specific "id" of file call RESTful API to get JSON information
          info("DELETE /api/collections/" + coll_id)
          val Some(result_get) = route(FakeRequest(DELETE, "/api/collections/" + coll_id + "?key=" + secretKey))
          //val Some(result_get) = route(FakeRequest(DELETE, "/api/collections/54ff8b41eb1f14ebbe00e5d2?key=" + secretKey))
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


 } // End Test Suite Bracket
} // End Class Bracket
