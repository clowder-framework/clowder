package integration

import org.scalatestplus.play.{PlaySpec, _}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, _}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Logger, Play}

import scala.io.Source

/*
 * Integration tests for Collections API - Router test
 * @author Eugene Roeder
 * 
 */


//@DoNotDiscover
class CollectionsAPIAppSpec extends PlaySpec with ConfiguredApp with FakeMultipartUpload {

  lazy val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
  lazy val workingDir = System.getProperty("user.dir")

  var collection1Id: String = ""
  var collection2Id: String = ""
  var zipFile1Id: String = ""
  var zipFile2Id: String = ""
  var dataset1Id: String =""
  var dataset2Id: String =""

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
      //link up json file here before fake request.
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/collections/data-test-collection.json")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      info("File Pathing " + file1.toString)
      val json_data_from_file_source = Source.fromFile(file1.toString)
      val json_data_from_file_lines = json_data_from_file_source.mkString
      json_data_from_file_source.close()

      // Place file string into a JSON object
      val json_tags: JsValue = Json.parse(json_data_from_file_lines)
      val readableString_tags: String = Json.prettyPrint(json_tags)
      info("Pretty JSON format")
      info(readableString_tags)          

      //link up json file here before fake request.
      val Some(result) = route(FakeRequest(POST, "/api/collections?key=" + secretKey).withJsonBody(json_tags))

      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include("id")
      collection1Id = contentAsJson(result).\("id").as[String]
      info("content"+contentAsString(result))
    }


    "respond to the createCollection() function routed by POST /api/collections for Collection 2" in {
      //link up json file here before fake request.
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/collections/data-test-collection-1.json")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      info("File Pathing " + file1.toString)
      val json_data_from_file_source = Source.fromFile(file1.toString)
      val json_data_from_file_lines = json_data_from_file_source.mkString
      json_data_from_file_source.close()

      // Place file string into a JSON object
      val json_tags: JsValue = Json.parse(json_data_from_file_lines)
      val readableString_tags: String = Json.prettyPrint(json_tags)
      info("Pretty JSON format")
      info(readableString_tags)          

      //link up json file here before fake request.
      val Some(result) = route(FakeRequest(POST, "/api/collections?key=" + secretKey).withJsonBody(json_tags))

      info("Status="+status(result))
      status(result) mustEqual OK
      info("contentType="+contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include("id")
      collection2Id = contentAsJson(result).\("id").as[String]
      info("content"+contentAsString(result))
    }

    "respond to the createDataset() function routed by POST /api/datasets for Dataset 1 Creation" in {
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/collections/dataset-image.zip")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }

      val req1 = FakeRequest(POST, "/api/extractions/upload_file?key=" + secretKey).
        withFileUpload("File", file1, "application/x-zip-compressed")
      val result1 = route(req1).get
      contentAsString(result1) must include ("id")
      zipFile1Id = contentAsJson(result1).\("id").as[String]

      val name = "Dataset 1 API Test Creation"
      val description = "Part 1 of Dataset API Test Suite"

      val req2 = FakeRequest(POST, "/api/datasets?key=" + secretKey).
          withJsonBody(Json.toJson(Map("name" -> name, "description" -> description, "file_id" -> zipFile1Id)))
      val result2 = route(req2).get

      info("Status=" + status(result2))
      status(result2) mustEqual OK
      info("contentType=" + contentType(result2))
      contentType(result2) mustEqual Some("application/json")
      contentAsString(result2) must include("id")
      dataset1Id = contentAsJson(result2).\("id").as[String]
      info("contentAsString" + contentAsString(result2))
    }

    "respond to the createDataset() function routed by POST /api/datasets for Dataset 2 Creation" in {
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/collections/dataset-image-1.zip")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }

      val req1 = FakeRequest(POST, "/api/extractions/upload_file?key=" + secretKey).
        withFileUpload("File", file1, "application/x-zip-compressed")
      val result1 = route(req1).get
      contentAsString(result1) must include ("id")
      zipFile2Id = contentAsJson(result1).\("id").as[String]

      val name = "Dataset 2 API Test Creation"
      val description = "Part 2 of Dataset API Test Suite"
      //val file_id = ""

      val req2 = FakeRequest(POST, "/api/datasets?key=" + secretKey).
          withJsonBody(Json.toJson(Map("name" -> name, "description" -> description, "file_id" -> zipFile2Id, "space" -> "default")))
      val result2 = route(req2).get

      info("Status=" + status(result2))
      status(result2) mustEqual OK
      info("contentType=" + contentType(result2))
      contentType(result2) mustEqual Some("application/json")
      contentAsString(result2) must include("id")
      dataset2Id = contentAsJson(result2).\("id").as[String]
      info("contentAsString" + contentAsString(result2))
    }

    "respond to the attachDataset(coll_id:UUID, ds_id:UUID) function routed by POST /api/collections/:coll_id/datasets/:ds_id for dataset 1" in {
      val req = FakeRequest(POST, "/api/collections/" + collection1Id + "/datasets/" + dataset1Id + "?key=" + secretKey)
      val result = route(req).get

      info("Status=" + status(result))
      status(result) mustEqual OK
      info("contentType=" + contentType(result))
      contentType(result) mustEqual Some("application/json")
      info("contentAsString" + contentAsString(result))
      // TODO check make sure dataset2 is added
    }

    "respond to the listInCollection(coll_id: UUID) function routed by GET /api/collections/:coll_id/getDatasets" in {
      val Some(result_get) = route(FakeRequest(GET, "/api/collections/" + collection1Id + "/getDatasets?key=" + secretKey))
      info("Status_Get="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      // TODO check make sure dataset1 is listed
    }

    "respond to the listOutsideCollection(coll_id: UUID) function routed by GET /api/datasets/listOutsideCollection/:coll_id" in {
      val Some(result_get) = route(FakeRequest(GET, "/api/datasets/listOutsideCollection/" + collection1Id + "?key=" + secretKey))
      info("Status_Get="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
      // TODO check make sure dataset2 is listed
    }


    "respond to the attachDataset(coll_id:UUID, ds_id:UUID) function routed by POST /api/collections/:coll_id/datasets/:ds_id for dataset 2" in {
      val req = FakeRequest(POST, "/api/collections/" + collection1Id + "/datasets/" + dataset2Id + "?key=" + secretKey)
      //.withJsonBody(Json.toJson(Map("name" -> name, "description" -> description, "file_id" -> file_id)))
      val result3 = route(req).get

      info("Status=" + status(result3))
      status(result3) mustEqual OK
      info("contentType=" + contentType(result3))
      contentType(result3) mustEqual Some("application/json")
      //contentAsString(result3) must include("id")
      info("contentAsString" + contentAsString(result3))
      // TODO check make sure dataset is really added
    }

    "respond to the removeDataset(coll_id:UUID, ds_id:UUID, ignoreNotFound) function routed by DELETE /api/collections/:coll_id/datasets/:ds_id for dataset 2" in {
      val req = FakeRequest(DELETE, "/api/collections/" + collection1Id + "/datasets/" + dataset2Id + "?key=" + secretKey)
      val result3 = route(req).get

      info("Status=" + status(result3))
      status(result3) mustEqual OK
      info("contentType=" + contentType(result3))
      contentType(result3) mustEqual Some("application/json")
      //contentAsString(result3) must include("id")
      info("contentAsString" + contentAsString(result3))
      // TODO check make sure dataset is really gone
    }

    "respond to the removeDataset(coll_id:UUID, ds_id:UUID, ignoreNoteFound) function routed by POST /api/collections/:coll_id/datasets/:ds_id/:ignoreNotFound for dataset 2" in {
      val req = FakeRequest(POST, "/api/collections/" + collection1Id + "/datasetsRemove/" + dataset1Id + "/true?key=" + secretKey)
      //.withJsonBody(Json.toJson(Map("name" -> name, "description" -> description, "file_id" -> file_id)))
      val result3 = route(req).get

      info("Status=" + status(result3))
      status(result3) mustEqual OK
      info("contentType=" + contentType(result3))
      contentType(result3) mustEqual Some("application/json")
      //contentAsString(result3) must include("id")
      info("contentAsString" + contentAsString(result3))
      // TODO check make sure dataset is really gone
    }


   "respond to the deleteDataset(id:UUID) function routed by DELETE /api/datasets/:id for Dataset 1 " in {
     val Some(result_get) = route(FakeRequest(DELETE, "/api/datasets/" + dataset1Id + "?key=" + secretKey))
     info("Status_Get="+status(result_get))
     status(result_get) mustEqual OK
     info("contentType_Get="+contentType(result_get))
     contentType(result_get) mustEqual Some("application/json")
     val json: JsValue = Json.parse(contentAsString(result_get))
     val readableString: String = Json.prettyPrint(json)
     info("Pretty JSON format")
     info(readableString)
   }

   "respond to the deleteDataset(id:UUID) function routed by DELETE /api/datasets/:id for Dataset 2 " in {
     val Some(result_get) = route(FakeRequest(DELETE, "/api/datasets/" + dataset2Id + "?key=" + secretKey))
     info("Status_Get="+status(result_get))
     status(result_get) mustEqual OK
     info("contentType_Get="+contentType(result_get))
     contentType(result_get) mustEqual Some("application/json")
     val json: JsValue = Json.parse(contentAsString(result_get))
     val readableString: String = Json.prettyPrint(json)
     info("Pretty JSON format")
     info(readableString)
   }

 "respond to the listCollections() function routed by GET /api/collections" in {
      val Some(result) = route(FakeRequest(GET, "/api/collections?key=" + secretKey))
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
      val Some(result_get) = route(FakeRequest(POST, "/api/collections/" + collection1Id + "/remove?key=" + secretKey))
      //val Some(result_get) = route(FakeRequest(DELETE, "/api/collections/54ff8b41eb1f14ebbe00e5d2?key=" + secretKey))
      info("Status_Get="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
    }

    "respond to the removeCollection(coll_id:UUID) function routed by DELETE /api/collections/:id  " in {
      val Some(result_get) = route(FakeRequest(DELETE, "/api/collections/" + collection2Id + "?key=" + secretKey))
      //val Some(result_get) = route(FakeRequest(DELETE, "/api/collections/54ff8b41eb1f14ebbe00e5d2?key=" + secretKey))
      info("Status_Get="+status(result_get))
      status(result_get) mustEqual OK
      info("contentType_Get="+contentType(result_get))
      contentType(result_get) mustEqual Some("application/json")
      val json: JsValue = Json.parse(contentAsString(result_get))
      val readableString: String = Json.prettyPrint(json)
      info("Pretty JSON format")
      info(readableString)
    }


 } // End Test Suite Bracket
} // End Class Bracket
