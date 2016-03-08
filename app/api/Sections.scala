package api

import javax.inject.{Inject, Singleton}

import com.wordnik.swagger.annotations.ApiOperation
import models.{Comment, UUID}
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.json.Json._
import models.{ResourceRef, UUID, Comment}
import services._
import javax.inject.{Inject, Singleton}
import services._

/**
 * Files sections.
 */
@Singleton
class Sections @Inject()(
  files: FileService,
  datasets: DatasetService,
  queries: MultimediaQueryService,
  tags: TagService,
  sections: SectionService,
  comments: CommentService,
  thumbnails: ThumbnailService) extends ApiController {

  /**
   * REST endpoint: POST: Add a section.
   * Requires that the request body contains a valid ObjectId in the "file_id" field,
   * otherwise returns a BadRequest.
   * A new ObjectId is created for this section.
   */
  def add() = PermissionAction(Permission.CreateSection)(parse.json) { implicit request =>
    if (request.user.isEmpty) Unauthorized("Not authorized")
      request.body.\("file_id").asOpt[String] match {
        case Some(file_id) => {
            files.get(UUID(file_id.toString)) match {
              case Some(file) =>
                val id = sections.insert(request.body)
                Ok(Json.obj("id" -> id))
              case None => {
                Logger.error("The file_id " + file_id + " is not found, request body: " + request.body)
                NotFound(toJson("The file_id " + file_id + " is not found."))
              }
            }
        }
        case None => {
          Logger.error("No \"file_id\" specified, request body: " + request.body)
          BadRequest(toJson("No \"file_id\" specified."))
        }
      }
  }

  @ApiOperation(value = "Delete section",
    notes = "Remove section file from system).",
    responseClass = "None", httpMethod = "DELETE")
  def delete(id: UUID) = PermissionAction(Permission.DeleteSection, Some(ResourceRef(ResourceRef.section, id))) {
    request =>
      sections.get(id) match {
        case Some(s) => {
          sections.removeSection(s)
          Ok(toJson(Map("status"->"success")))
        }
        case None => Ok(toJson(Map("status" -> "success")))
      }
  }

  /**
   * REST endpoint: GET: get info of this section.
   */
  def get(id: UUID) = PermissionAction(Permission.ViewSection, Some(ResourceRef(ResourceRef.section, id))) { implicit request =>
      Logger.info("Getting info for section with id " + id)
      sections.get(id) match {
        case Some(section) =>
          Ok(Json.obj("id" -> section.id.toString, "file_id" -> section.file_id.toString,
            "startTime" -> section.startTime.getOrElse(-1).toString, "tags" -> Json.toJson(section.tags.map(_.name))))
        case None => Logger.error("Section not found " + id); NotFound(toJson("Section not found, id: " + id))
      }
  }

  // ---------- Tags related code starts ------------------
  /**
   * REST endpoint: GET: get the tag data associated with this section.
   * Returns a JSON object of multiple fields.
   * One returned field is "tags", containing a list of string values.
   */
  def getTags(id: UUID) = PermissionAction(Permission.ViewSection, Some(ResourceRef(ResourceRef.section, id))) { implicit request =>
      Logger.info("Getting tags for section with id " + id)
      sections.get(id) match {
        case Some(section) =>
          Ok(Json.obj("id" -> section.id.toString, "file_id" -> section.file_id.toString,
            "tags" -> Json.toJson(section.tags.map(_.name))))
        case None => {
          Logger.error("The section with id " + id + " is not found.")
          NotFound(toJson("The section with id " + id + " is not found."))
        }
      }
  }

  /**
   * REST endpoint: POST: Add tags to a section.
   * Requires that the request body contains a "tags" field of List[String] type.
   */
  def addTags(id: UUID) = PermissionAction(Permission.AddTag, Some(ResourceRef(ResourceRef.section, id)))(parse.json) { implicit request =>
      val (not_found, error_str) = tags.addTagsHelper(TagCheck_Section, id, request)

      // Now the real work: adding the tags.
      if ("" == error_str) {
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

  /**
   * REST endpoint: POST: remove tags.
   * Requires that the request body contains a "tags" field of List[String] type.
   */
  def removeTags(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.section, id)))(parse.json) { implicit request =>
      val (not_found, error_str) = tags.removeTagsHelper(TagCheck_Section, id, request)

      if ("" == error_str) {
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

  /**
   * REST endpoint: POST: remove all tags.
   */
  def removeAllTags(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.section, id))) { implicit request =>
      Logger.info("Removing all tags for section with id: " + id)
      sections.get(id) match {
        case Some(section) => {
          sections.removeAllTags(id)
          Ok(Json.obj("status" -> "success"))
        }
        case None => {
          Logger.error("The section with id " + id + " is not found.")
          NotFound(toJson("The section with id " + id + " is not found."))
        }
      }
  }

  // ---------- Tags related code ends ------------------

  def comment(id: UUID) = PermissionAction(Permission.AddComment, Some(ResourceRef(ResourceRef.section, id)))(parse.json) { implicit request =>
      request.user match {
        case Some(identity) => {
          (request.body \ "text").asOpt[String] match {
            case Some(text) => {
              val comment = new Comment(identity.getMiniUser, text, section_id = Some(id))
              comments.insert(comment)
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
  
  def description(id: UUID) = PermissionAction(Permission.EditSection, Some(ResourceRef(ResourceRef.section, id)))(parse.json)  { implicit request =>
	  request.user match {
	    case Some(identity) => {
		    request.body.\("description").asOpt[String] match {
			    case Some(descr) => {
			        sections.setDescription(id, descr)
			        Ok(toJson(Map("status"->"success")))
			    }
			    case None => {
			    	Logger.error("no section description specified.")
			    	BadRequest(toJson("no section description specified."))
			    }
		    }
	    }
	    case None =>
	      Logger.error(("No user identity found in the request, request body: " + request.body))
	      BadRequest(toJson("No user identity found in the request, request body: " + request.body))
	  }
    }

  /**
   * Add thumbnail to section.
   */
  def attachThumbnail(section_id: UUID, thumbnail_id: UUID) = PermissionAction(Permission.EditSection, Some(ResourceRef(ResourceRef.section, section_id))) { implicit request =>
      sections.get(section_id) match {
        case Some(section) => {
          thumbnails.get(thumbnail_id) match {
            case Some(thumbnail) => {
              sections.updateThumbnail(section_id, thumbnail_id)              
              Ok(toJson(Map("status" -> "success")))
            }
            case None => BadRequest(toJson("Thumbnail not found"))
          }
        }
        case None => BadRequest(toJson("Section not found " + section_id))
      }
  }
  
}