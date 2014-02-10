/**
 *
 */
package api

import java.util.Date
import java.util.ArrayList
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import models._
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json._
import jsonutils.JsonUtil
import scala.collection.mutable.ListBuffer
import services._
import controllers.Previewers
import play.api.libs.json.JsString
import org.bson.types.ObjectId
import play.api.Play.current
import javax.inject.{ Singleton, Inject }
import com.mongodb.casbah.Imports._
import com.mongodb.WriteConcern
import scala.util.parsing.json.JSONArray
import org.json.JSONObject
import Transformation.LidoToCidocConvertion
import java.io.BufferedWriter
import java.io.BufferedReader
import java.io.FileWriter
import java.io.FileReader
import play.api.libs.iteratee.Enumerator
import java.io.FileInputStream
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsString
import scala.Some
import api.RequestWithUser
import api.WithPermission
import models.File
import play.api.libs.json.JsString
import scala.Some
import api.RequestWithUser
import api.WithPermission
import models.File
import play.api.libs.json.JsString
import scala.Some
import api.RequestWithUser
import api.WithPermission
import models.File

import org.apache.commons.io.FileUtils

/**
 * Dataset API.
 *
 * @author Luigi Marini
 *
 */
object ActivityFound extends Exception {}

@Api(value = "/datasets", listingPath = "/api-docs.{format}/datasets", description = "Maniputate datasets")
@Singleton
class Datasets @Inject() (datasets: DatasetService, files: FileService) extends ApiController {

  /**
   * List all datasets.
   */
  def list = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ListDatasets)) { request =>
    val list = for (dataset <- datasets.listDatasets()) yield jsonDataset(dataset)
    Ok(toJson(list))
  }

  /**
   * List all datasets outside a collection.
   */
  def listOutsideCollection(collectionId: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ListDatasets)) { request =>
    Collection.findOneById(new ObjectId(collectionId)) match {
      case Some(collection) => {
        val list = for (dataset <- datasets.listDatasetsChronoReverse; if (!isInCollection(dataset, collection))) yield jsonDataset(dataset)
        Ok(toJson(list))
      }
      case None => {
        val list = for (dataset <- datasets.listDatasetsChronoReverse) yield jsonDataset(dataset)
        Ok(toJson(list))
      }
    }
  }

  def isInCollection(dataset: Dataset, collection: Collection): Boolean = {
    for (collDataset <- collection.datasets) {
      if (collDataset.id == dataset.id)
        return true
    }
    return false
  }

  /**
   * Create new dataset
   */
    def createDataset() = SecuredAction(authorization=WithPermission(Permission.CreateDatasets)) { request =>
      Logger.debug("Creating new dataset")
      (request.body \ "name").asOpt[String].map { name =>
      	  (request.body \ "description").asOpt[String].map { description =>
      	    (request.body \ "file_id").asOpt[String].map { file_id =>
      	      FileDAO.get(file_id) match {
      	        case Some(file) =>
      	           val d = Dataset(name=name,description=description, created=new Date(), files=List(file), author=request.user.get)
		      	   Dataset.insert(d) match {
		      	     case Some(id) => {
                       import play.api.Play.current
                       files.index(file_id)
                       if(!file.xmlMetadata.isEmpty){
                         val xmlToJSON = FileDAO.getXMLMetadataJSON(file_id)
                         Dataset.addXMLMetadata(id.toString, file_id, xmlToJSON)
                         current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", id.toString,
                           List(("name",d.name), ("description", d.description), ("xmlmetadata", xmlToJSON)))}
                       }
                       else{
                         current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", id.toString,
                           List(("name",d.name), ("description", d.description)))}
                       }
                       Ok(toJson(Map("id" -> id.toString)))
		      	     }
		      	     case None => Ok(toJson(Map("status" -> "error")))
		      	   }
      	        case None => BadRequest(toJson("Bad file_id = " + file_id))
      	      }
      	   }.getOrElse {
      		  BadRequest(toJson("Missing parameter [file_id]"))
      	   }
      	  }.getOrElse {
      		BadRequest(toJson("Missing parameter [description]"))
      	  }
    }.getOrElse {
      BadRequest(toJson("Missing parameter [name]"))
    }
  }

  def attachExistingFile(dsId: String, fileId: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.CreateDatasets)) { request =>
    datasets.get(dsId) match {
      case Some(dataset) => {
        files.get(fileId) match {
          case Some(file) => {
            val theFile = FileDAO.get(fileId).get
            if(!isInDataset(theFile,dataset)){
	            Dataset.addFile(dsId, theFile)	            
	            files.index(fileId)
	            if(!theFile.xmlMetadata.isEmpty){
	            	index(dsId)
		      	}	            
       
	            if(dataset.thumbnail_id.isEmpty && !theFile.thumbnail_id.isEmpty){
		                        Dataset.dao.collection.update(MongoDBObject("_id" -> dataset.id), 
		                        $set("thumbnail_id" -> theFile.thumbnail_id), false, false, WriteConcern.SAFE)
		        }
	            
	            //add file to RDF triple store if triple store is used
	            if(theFile.filename.endsWith(".xml")){
			             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
				             case "yes" => {
				               services.Services.rdfSPARQLService.linkFileToDataset(fileId, dsId)
				             }
				             case _ => {}
			             }
			     }

		       Logger.info("Adding file to dataset completed")                 
		                        
		       
            }
            else{
              Logger.info("File was already in dataset.")
            }
            Ok(toJson(Map("status" -> "success")))
          }
          case None => { Logger.error("Error getting file" + fileId); InternalServerError }
        }        
      }
      case None => { Logger.error("Error getting dataset" + dsId); InternalServerError }
    }  
  }
  
  def isInDataset(file: File, dataset: Dataset): Boolean = {
    for(dsFile <- dataset.files){
      if(dsFile.id == file.id)
        return true
    }
    return false
  }
  
  def detachFile(datasetId: String, fileId: String, ignoreNotFound: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.CreateCollections)) { request =>
    datasets.get(datasetId) match{
      case Some(dataset) => {
        files.get(fileId) match {
          case Some(file) => {
            val theFile = FileDAO.get(fileId).get
            if(isInDataset(theFile,dataset)){
	            //remove file from dataset
	            Dataset.removeFile(dataset.id.toString, theFile.id.toString)
	            files.index(fileId)
	            if(!theFile.xmlMetadata.isEmpty){
	            	index(datasetId)
		      	}
	            Logger.info("Removing file from dataset completed")
	            
	            if(!dataset.thumbnail_id.isEmpty && !theFile.thumbnail_id.isEmpty){
	              if(dataset.thumbnail_id.get == theFile.thumbnail_id.get){
		             Dataset.newThumbnail(dataset.id.toString)
		          }		                        
		       }
	            
	           //remove link between dataset and file from RDF triple store if triple store is used
	            if(theFile.filename.endsWith(".xml")){
			        play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
				        case "yes" => {
				        	services.Services.rdfSPARQLService.detachFileFromDataset(fileId, datasetId)
				        }
				        case _ => {}
			        }
		        } 
	            
            }
            else{
              Logger.info("File was already out of the dataset.")
            }
            Ok(toJson(Map("status" -> "success")))
          }
          case None => {
        	  Ok(toJson(Map("status" -> "success")))
          }
        }
      }
      case None => {
        ignoreNotFound match{
          case "True" =>
            Ok(toJson(Map("status" -> "success")))
          case "False" =>
        	Logger.error("Error getting dataset" + datasetId); InternalServerError
        }
      }     
    }
  }
  
  def getInCollection(collectionId: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowCollection)) { request =>
    Collection.findOneById(new ObjectId(collectionId)) match{
      case Some(collection) => {
        val list = for (dataset <- datasets.listInsideCollection(collectionId)) yield jsonDataset(dataset)
        Ok(toJson(list))
      }
      case None => {
        Logger.error("Error getting collection" + collectionId); InternalServerError
      }
    }
  }

  def jsonDataset(dataset: Dataset): JsValue = {
    var datasetThumbnail = "None"
    if(!dataset.thumbnail_id.isEmpty)
      datasetThumbnail = dataset.thumbnail_id.toString().substring(5,dataset.thumbnail_id.toString().length-1)
    
    toJson(Map("id" -> dataset.id.toString, "datasetname" -> dataset.name, "description" -> dataset.description, "created" -> dataset.created.toString, "thumbnail" -> datasetThumbnail))
  }

  @ApiOperation(value = "Add metadata to dataset", notes = "Returns success of failure", responseClass = "None", httpMethod = "POST")
  def addMetadata(id: String) = SecuredAction(authorization=WithPermission(Permission.AddDatasetsMetadata)) { request =>
      Logger.debug("Adding metadata to dataset " + id)
      Dataset.addMetadata(id, Json.stringify(request.body))
      index(id)
      Ok(toJson(Map("status" -> "success")))
  }

  def addUserMetadata(id: String) = SecuredAction(authorization=WithPermission(Permission.AddDatasetsMetadata)) { request =>
      Logger.debug("Adding user metadata to dataset " + id)
      Dataset.addUserMetadata(id, Json.stringify(request.body))
      index(id)
      play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
		      case "yes" => {
		          Dataset.setUserMetadataWasModified(id, true)
		    	  //modifyRDFUserMetadata(id)
		      }
		      case _ => {}
	      }
      
      Ok(toJson(Map("status" -> "success")))
    }
  

  def datasetFilesGetIdByDatasetAndFilename(datasetId: String, filename: String): Option[String] = {
    datasets.get(datasetId) match {
      case Some(dataset) => {  
        for (file <- dataset.files) {
          if (file.filename.equals(filename)) {
            return Some(file.id.toString)
          }
        }
        Logger.error("File does not exist in dataset" + datasetId); return None
      }
      case None => { Logger.error("Error getting dataset" + datasetId); return None }
    }
  }

  def datasetFilesList(id: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowDataset)) { request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val list = for (f <- dataset.files) yield jsonFile(f)
        Ok(toJson(list))
      }
      case None => { Logger.error("Error getting dataset" + id); InternalServerError }
    }
  }

  def jsonFile(file: File): JsValue = {
    toJson(Map("id" -> file.id.toString, "filename" -> file.filename, "contentType" -> file.contentType, "date-created" -> file.uploadDate.toString(), "size" -> file.length.toString))
  }

  def index(id: String) {
    Dataset.index(id)
  }

  // ---------- Tags related code starts ------------------
  /**
   *  REST endpoint: GET: get the tag data associated with this section.
   *  Returns a JSON object of multiple fields.
   *  One returned field is "tags", containing a list of string values.
   */
  @ApiOperation(value = "Get the tags associated with this dataset", notes = "Returns a JSON object of multiple fields", responseClass = "None", httpMethod = "GET")
  def getTags(id: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) { implicit request =>
    Logger.info("Getting tags for dataset with id " + id)
    /* Found in testing: given an invalid ObjectId, a runtime exception
     * ("IllegalArgumentException: invalid ObjectId") occurs.  So check it first.
     */
    if (ObjectId.isValid(id)) {
      datasets.get(id) match {
        case Some(dataset) =>
          Ok(Json.obj("id" -> dataset.id.toString, "name" -> dataset.name, "tags" -> Json.toJson(dataset.tags.map(_.name))))
        case None => {
          Logger.error("The dataset with id " + id + " is not found.")
          NotFound(toJson("The dataset with id " + id + " is not found."))
        }
      }
    } else {
      Logger.error("The given id " + id + " is not a valid ObjectId.")
      BadRequest(toJson("The given id " + id + " is not a valid ObjectId."))
    }
  }
  
  def removeTag(id: String) = SecuredAction(parse.json,authorization=WithPermission(Permission.DeleteTags)) {implicit request =>
    Logger.debug("Removing tag " + request.body)
    
    request.body.\("tagId").asOpt[String].map { tagId =>
		  Logger.debug("Removing " + tagId + " from "+ id)
          Dataset.removeTag(id, tagId)
		}
      Ok(toJson(""))    
  }

  /**
   *  REST endpoint: POST: Add tags to a dataset.
   *  Requires that the request body contains a "tags" field of List[String] type.
   */
  def addTags(id: String) = SecuredAction(authorization = WithPermission(Permission.CreateTags)) { implicit request =>
    addTagsHelper(TagCheck_Dataset, id, request)
  }

  /**
   *  REST endpoint: POST: remove tags.
   *  Requires that the request body contains a "tags" field of List[String] type.
   */
  def removeTags(id: String) = SecuredAction(authorization = WithPermission(Permission.DeleteTags)) { implicit request =>
    removeTagsHelper(TagCheck_Dataset, id, request)
  }

  /*
 *  Helper function to handle adding and removing tags for files/datasets/sections.
 *  Input parameters:
 *      obj_type: one of the three TagCheckObjType's: TagCheck_File, TagCheck_Dataset or TagCheck_Section
 *      op_type:  one of the two strings "add", "remove"
 *      id:       the id in the original addTags call
 *      request:  the request in the original addTags call
 *  Return type:
 *      play.api.mvc.SimpleResult[JsValue]
 *      in the form of Ok, NotFound and BadRequest
 *      where: Ok contains the JsObject: "status" -> "success", the other two contain a JsString,
 *      which contains the cause of the error, such as "No 'tags' specified", and
 *      "The file with id 5272d0d7e4b0c4c9a43e81c8 is not found".
 */
  def addTagsHelper(obj_type: TagCheckObjType, id: String, request: RequestWithUser[JsValue]) = {
    val tagCheck = checkErrorsForTag(obj_type, id, request)

    val error_str = tagCheck.error_str
    val not_found = tagCheck.not_found
    val userOpt = tagCheck.userOpt
    val extractorOpt = tagCheck.extractorOpt
    val tags = tagCheck.tags

    // Now the real work: adding the tags.
    if ("" == error_str) {
      // Clean up leading, trailing and multiple contiguous white spaces.
      val tagsCleaned = tags.get.map(_.trim().replaceAll("\\s+", " "))
      (obj_type) match {
        case TagCheck_File => FileDAO.addTags(id, userOpt, extractorOpt, tagsCleaned)
        case TagCheck_Dataset =>
          Dataset.addTags(id, userOpt, extractorOpt, tagsCleaned); Dataset.index(id)
        case TagCheck_Section => SectionDAO.addTags(id, userOpt, extractorOpt, tagsCleaned)
      }
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  def removeTagsHelper(obj_type: TagCheckObjType, id: String, request: RequestWithUser[JsValue]) = {
    val tagCheck = checkErrorsForTag(obj_type, id, request)

    val error_str = tagCheck.error_str
    val not_found = tagCheck.not_found
    val userOpt = tagCheck.userOpt
    val extractorOpt = tagCheck.extractorOpt
    val tags = tagCheck.tags

    // Now the real work: removing the tags.
    if ("" == error_str) {
      // Clean up leading, trailing and multiple contiguous white spaces.
      val tagsCleaned = tags.get.map(_.trim().replaceAll("\\s+", " "))
      (obj_type) match {
        case TagCheck_File => FileDAO.removeTags(id, userOpt, extractorOpt, tagsCleaned)
        case TagCheck_Dataset => Dataset.removeTags(id, userOpt, extractorOpt, tagsCleaned)
        case TagCheck_Section => SectionDAO.removeTags(id, userOpt, extractorOpt, tagsCleaned)
      }
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  // TODO: move helper methods to standalone service
  val USERID_ANONYMOUS = "anonymous"
  // Helper class and function to check for error conditions for tags.
  class TagCheck {
    var error_str: String = ""
    var not_found: Boolean = false
    var userOpt: Option[String] = None
    var extractorOpt: Option[String] = None
    var tags: Option[List[String]] = None
  }

 /*
 *  Helper function to check for error conditions.
 *  Input parameters:
 *      obj_type: one of the three TagCheckObjType's: TagCheck_File, TagCheck_Dataset or TagCheck_Section
 *      id:       the id in the original addTags call
 *      request:  the request in the original addTags call
 *  Returns:
 *      tagCheck: a TagCheck object, containing the error checking results:
 *
 *      If error_str == "", then no error is found;
 *      otherwise, it contains the cause of the error.
 *      not_found is one of the error conditions, meaning the object with
 *      the given id is not found in the DB.
 *      userOpt, extractorOpt and tags are set according to the request's content,
 *      and will remain None if they are not specified in the request.
 *      We change userOpt from its default None value, only if the userId
 *      is not USERID_ANONYMOUS.  The use case for this is the extractors
 *      posting to the REST API -- they'll use the commKey to post, and the original
 *      userId of these posts is USERID_ANONYMOUS -- in this case, we'd like to
 *      record the extractor_id, but omit the userId field, so we leave userOpt as None.
 */
  def checkErrorsForTag(obj_type: TagCheckObjType, id: String, request: RequestWithUser[JsValue]) : TagCheck = {
    val userObj = request.user
    Logger.debug("checkErrorsForTag: user id: " + userObj.get.identityId.userId + ", user.firstName: " + userObj.get.firstName
      + ", user.LastName: " + userObj.get.lastName + ", user.fullName: " + userObj.get.fullName)
    val userId = userObj.get.identityId.userId
    if (USERID_ANONYMOUS == userId) {
      Logger.debug("checkErrorsForTag: The user id is \"anonymous\".")
    }

    var userOpt: Option[String] = None
    var extractorOpt: Option[String] = None
    var error_str = ""
    var not_found = false
    val tags = request.body.\("tags").asOpt[List[String]]

    if (tags.isEmpty) {
      error_str = "No \"tags\" specified, request.body: " + request.body.toString
    } else if (!ObjectId.isValid(id)) {
      error_str = "The given id " + id + " is not a valid ObjectId."
    } else {
      obj_type match {
        case TagCheck_File => not_found = files.getFile(id).isEmpty
        case TagCheck_Dataset => not_found = datasets.get(id).isEmpty
        case TagCheck_Section => not_found = SectionDAO.findOneById(new ObjectId(id)).isEmpty
        case _ => error_str = "Only file/dataset/section is supported in checkErrorsForTag()."
      }
      if (not_found) {
        error_str = "The " + obj_type + " with id " + id + " is not found."
      }
    }
    if ("" == error_str) {
      if (USERID_ANONYMOUS == userId) {
        val eid = request.body.\("extractor_id").asOpt[String]
        eid match {
          case Some(extractor_id) => extractorOpt = eid
          case None => error_str = "No \"extractor_id\" specified, request.body: " + request.body.toString
        }
      } else {
        userOpt = Option(userId)
      }
    }
    val tagCheck = new TagCheck
    tagCheck.error_str = error_str
    tagCheck.not_found = not_found
    tagCheck.userOpt = userOpt
    tagCheck.extractorOpt = extractorOpt
    tagCheck.tags = tags
    tagCheck
  }

  /**
   *  REST endpoint: POST: remove all tags.
   */
  def removeAllTags(id: String) = SecuredAction(authorization = WithPermission(Permission.DeleteTags)) { implicit request =>
    Logger.info("Removing all tags for dataset with id: " + id)
    if (ObjectId.isValid(id)) {
      datasets.get(id) match {
        case Some(dataset) => {
          Dataset.removeAllTags(id)
          Ok(Json.obj("status" -> "success"))
        }
        case None => {
          Logger.error("The dataset with id " + id + " is not found.")
          NotFound(toJson("The dataset with id " + id + " is not found."))
        }
      }
    } else {
      Logger.error("The given id " + id + " is not a valid ObjectId.")
      BadRequest(toJson("The given id " + id + " is not a valid ObjectId."))
    }
  }
  // ---------- Tags related code ends ------------------
  
  
  
  
  def comment(id: String) = SecuredAction(authorization=WithPermission(Permission.CreateComments)) { implicit request =>
    request.user match {
      case Some(identity) => {
	    request.body.\("text").asOpt[String] match {
	      case Some(text) => {
	        val comment = new Comment(identity, text, dataset_id=Some(id))
	        Comment.save(comment)
	        index(id)
	        Ok(comment.id.toString())
	      }
	      case None => {
	        Logger.error("no text specified.")
	        BadRequest
	      }
	    }
      }
      case None => BadRequest
    }
  }

  /**
   * List datasets satisfying a user metadata search tree.
   */
  def searchDatasetsUserMetadata = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { request =>
    Logger.debug("Searching datasets' user metadata for search tree.")

    var searchJSON = Json.stringify(request.body)
    Logger.debug("thejsson: "+searchJSON)
    var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

    var searchQuery = Dataset.searchUserMetadataFormulateQuery(searchTree)

    //searchQuery = searchQuery.reverse

    Logger.debug("Search completed. Returning datasets list.")

    val list = for (dataset <- searchQuery) yield jsonDataset(dataset)
    Logger.debug("thelist: " + toJson(list))
    Ok(toJson(list))
  }
  
  /**
   * List datasets satisfying a general metadata search tree.
   */
  def searchDatasetsGeneralMetadata = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { request =>
      Logger.debug("Searching datasets' metadata for search tree.")
      
      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: "+searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]
      
      var searchQuery = Dataset.searchAllMetadataFormulateQuery(searchTree)
      
      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning datasets list.")

      val list = for (dataset <- searchQuery) yield jsonDataset(dataset)
      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
    } 
  
  /**
   * Return whether a dataset is currently being processed.
   */
  def isBeingProcessed(id: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowDataset)) { request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val files = dataset.files map { f =>
          FileDAO.get(f.id.toString).get
        }

        var isActivity = "false"
        try {
          for (f <- files) {
        		Extraction.findIfBeingProcessed(f.id) match{
        			case false => 
        			case true => { 
                    isActivity = "true"
                    throw ActivityFound
                  }
                }
          }
        } catch {
          case ActivityFound =>
        }

        Ok(toJson(Map("isBeingProcessed" -> isActivity)))
      }
      case None => { Logger.error("Error getting dataset" + id); InternalServerError }
    }
  }

  def jsonPreviewsFiles(filesList: List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]): JsValue = {
    val list = for (filePrevs <- filesList) yield jsonPreviews(filePrevs._1, filePrevs._2)
    toJson(list)
  }
  def jsonPreviews(prvFile: models.File, prvs: Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]): JsValue = {
    val list = for (prv <- prvs) yield jsonPreview(prv._1, prv._2, prv._3, prv._4, prv._5, prv._6, prv._7)
    val listJson = toJson(list.toList)
    toJson(Map[String, JsValue]("file_id" -> JsString(prvFile.id.toString), "previews" -> listJson))
  }
  def jsonPreview(pvId: java.lang.String, pId: String, pPath: String, pMain: String, pvRoute: java.lang.String, pvContentType: String, pvLength: Long): JsValue = {
    if (pId.equals("X3d"))
      toJson(Map("pv_id" -> pvId, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString, "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString,
        "pv_annotationsEditPath" -> api.routes.Previews.editAnnotation(pvId).toString, "pv_annotationsListPath" -> api.routes.Previews.listAnnotations(pvId).toString, "pv_annotationsAttachPath" -> api.routes.Previews.attachAnnotation(pvId).toString))
    else
      toJson(Map("pv_id" -> pvId, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString, "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString))
  }
  def getPreviews(id: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowDataset)) { request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val files = dataset.files map { f =>
          FileDAO.get(f.id.toString).get
        }

        val datasetWithFiles = dataset.copy(files = files)
        val previewers = Previewers.findPreviewers
        val previewslist = for(f <- datasetWithFiles.files; if(f.showPreviews.equals("DatasetLevel"))) yield {
          val pvf = for (p <- previewers; pv <- f.previews; if (p.contentType.contains(pv.contentType))) yield {
            (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
          }
          if (pvf.length > 0) {
            (f -> pvf)
          } else {
            val ff = for (p <- previewers; if (p.contentType.contains(f.contentType))) yield {
              (f.id.toString, p.id, p.path, p.main, controllers.routes.Files.file(f.id.toString) + "/blob", f.contentType, f.length)
            }
            (f -> ff)
          }
        }
        Ok(jsonPreviewsFiles(previewslist.asInstanceOf[List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]]))
      }
      case None => { Logger.error("Error getting dataset" + id); InternalServerError }
    }
  }

  def deleteDataset(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.DeleteDatasets)) { request =>
    datasets.get(id)  match {
      case Some(dataset) => {
        //remove dataset from RDF triple store if triple store is used
        play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
	        case "yes" => {
	            services.Services.rdfSPARQLService.removeDatasetFromGraphs(id)
	        }
	        case _ => {}
        }
        
        Dataset.removeDataset(id)
        
        Ok(toJson(Map("status"->"success")))
      }
      case None => Ok(toJson(Map("status"->"success")))
    }
  }
  
  
  def getRDFUserMetadata(id: String, mappingNumber: String="1") = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDatasetsMetadata)) {implicit request =>
    play.Play.application().configuration().getString("rdfexporter") match{
      case "on" =>{
        datasets.get(id) match { 
            case Some(dataset) => {
              val theJSON = Dataset.getUserMetadataJSON(id)
              val fileSep = System.getProperty("file.separator")
              val tmpDir = System.getProperty("java.io.tmpdir")
	          var resultDir = tmpDir + fileSep + "medici__rdfdumptemporaryfiles" + fileSep + new ObjectId().toString
	          new java.io.File(resultDir).mkdir()
              
              if(!theJSON.replaceAll(" ","").equals("{}")){
	              val xmlFile = jsonToXML(theJSON)	              	              
	              new LidoToCidocConvertion(play.api.Play.configuration.getString("datasetsxmltordfmapping.dir_"+mappingNumber).getOrElse(""), xmlFile.getAbsolutePath(), resultDir)	                            
	              xmlFile.delete()
              }
              else{
                new java.io.File(resultDir + fileSep + "Results.rdf").createNewFile()
              }
              val resultFile = new java.io.File(resultDir + fileSep + "Results.rdf")
              
              Ok.chunked(Enumerator.fromStream(new FileInputStream(resultFile)))
		            	.withHeaders(CONTENT_TYPE -> "application/rdf+xml")
		            	.withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + resultFile.getName()))
            }
            case None => BadRequest(toJson("Dataset not found " + id))
        }
      }
      case _ => {
        Ok("RDF export features not enabled")
      }      
    }
  }
  
  def jsonToXML(theJSON: String): java.io.File = {
    
    val jsonObject = new JSONObject(theJSON)    
    var xml = org.json.XML.toString(jsonObject)
    
    Logger.debug("thexml: " + xml)
    
    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while(currStart != -1){
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd+1,currStart)
      currEnd = xml.indexOf(">", currStart+1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart,currEnd+1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd+1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd+1)
    
    val xmlFile = java.io.File.createTempFile("xml",".xml")
    val fileWriter =  new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()
    
    return xmlFile    
  }
  
  def getRDFURLsForDataset(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDatasetsMetadata)) { request =>
    play.Play.application().configuration().getString("rdfexporter") match{
      case "on" =>{
	    datasets.get(id)   match {
	      case Some(dataset) => {
	        
	        //RDF from XML files in the dataset itself (for XML metadata-only files)
	        val previewsList = PreviewDAO.findByDatasetId(new ObjectId(id))
	        var rdfPreviewList = List.empty[models.Preview]
	        for(currPreview <- previewsList){
	          if(currPreview.contentType.equals("application/rdf+xml")){
	            rdfPreviewList = rdfPreviewList :+ currPreview
	          }
	        }        
	        var hostString = "http://" + request.host + request.path.replaceAll("datasets/getRDFURLsForDataset/[A-Za-z0-9_]*$", "previews/")
	        var list = for (currPreview <- rdfPreviewList) yield Json.toJson(hostString + currPreview.id.toString)
	        
	        for(file <- dataset.files){
	           val filePreviewsList = PreviewDAO.findByFileId(file.id)
	           var fileRdfPreviewList = List.empty[models.Preview]
	           for(currPreview <- filePreviewsList){
		           if(currPreview.contentType.equals("application/rdf+xml")){
		        	   fileRdfPreviewList = fileRdfPreviewList :+ currPreview
		           }
	           }
	           val filesList = for (currPreview <- fileRdfPreviewList) yield Json.toJson(hostString + currPreview.id.toString)
	           list = list ++ filesList
	        }
	        
	        //RDF from export of dataset community-generated metadata to RDF
	        var connectionChars = ""
			if(hostString.contains("?")){
				connectionChars = "&mappingNum="
			}
			else{
				connectionChars = "?mappingNum="
			}        
	        hostString = "http://" + request.host + request.path.replaceAll("/getRDFURLsForDataset/", "/rdfUserMetadataDataset/") + connectionChars
	        
	        val mappingsQuantity = Integer.parseInt(play.api.Play.configuration.getString("datasetsxmltordfmapping.dircount").getOrElse("1"))
	        for(i <- 1 to mappingsQuantity){
	          var currHostString = hostString + i
	          list = list :+ Json.toJson(currHostString)
	        }
	
	        val listJson = toJson(list.toList)
	        
	        Ok(listJson) 
	      }
	      case None => {Logger.error("Error getting dataset" + id); InternalServerError}
	    }
      }
      case _ => {
        Ok("RDF export features not enabled")
      }
    }
  }
  
  def getTechnicalMetadataJSON(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDatasetsMetadata)) { request =>
    datasets.get(id)  match {
      case Some(dataset) => {
        Ok(Dataset.getTechnicalMetadataJSON(id))
      }
      case None => {Logger.error("Error finding dataset" + id); InternalServerError}      
    }
  }
  def getXMLMetadataJSON(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDatasetsMetadata)) { request =>
    datasets.get(id)  match {
      case Some(dataset) => {
        Ok(Dataset.getXMLMetadataJSON(id))
      }
      case None => {Logger.error("Error finding dataset" + id); InternalServerError}      
    }
  }
  def getUserMetadataJSON(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDatasetsMetadata)) { request =>
    datasets.get(id)  match {
      case Some(dataset) => {
        Ok(Dataset.getUserMetadataJSON(id))
      }
      case None => {Logger.error("Error finding dataset" + id); InternalServerError}      
    }
  }
  
}
