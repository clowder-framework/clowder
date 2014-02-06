package services.mongodb

import play.api.libs.json.JsValue
import play.api.Logger
import org.bson.types.ObjectId
import models.{Dataset, FileDAO, SectionDAO}
import javax.inject.Inject
import services._
import api.RequestWithUser
import scala.Some

/**
 * Created by lmarini on 1/17/14.
 */
class MongoDBTagService @Inject() (files: FileService, datasets: DatasetService, queries: QueryService) extends TagService {

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
  def addTagsHelper(obj_type: TagCheckObjType, id: String, request: RequestWithUser[JsValue]): (Boolean, String) = {
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
    }
    (not_found, error_str)
  }

  def removeTagsHelper(obj_type: TagCheckObjType, id: String, request: RequestWithUser[JsValue]): (Boolean, String) = {
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
    }
    (not_found, error_str)
  }

}
