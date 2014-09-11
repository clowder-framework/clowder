package api

import org.bson.types.ObjectId
import play.api.libs.json._
import play.api.libs.json.Json._
import play.Logger
import java.util.Date
import play.api.Play.current
import javax.inject.Inject
import services.{CommentService, DatasetService, ElasticsearchPlugin}
import models.UUID
import com.wordnik.swagger.annotations.{ApiOperation, Api}

/**
 * Comments on datasets.
 *
 * @author Rob Kooper
 */
class Comments @Inject()(datasets: DatasetService, comments: CommentService) extends ApiController {

  def comment(id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateComments)) {
    implicit request =>
      comments.get(id) match {          
        case Some(parent) => {
          request.user match {
            case Some(identity) => {
              request.body.\("text").asOpt[String] match {
                case Some(text) => {
                  val comment = parent.copy(comment_id = Some(id), author = identity, text = text, posted = new Date())
                  comments.insert(comment)
                  if (parent.dataset_id.isDefined) {
                    datasets.get(parent.dataset_id.get) match {
                      case Some(dataset) => {
                        current.plugin[ElasticsearchPlugin].foreach {
                          _.indexDataset(dataset)
                        }
                      }
                      case None => Logger.error("Dataset not found: " + id)
                    }
                  }
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
        case None => BadRequest
      }
  }
    
  //Remove comment code starts
  /**
   * REST endpoint: DELETE: remove a comment associated with a specific file
   * 
   *  The method takes a single arg:
   *  
   *  id: A UUID that identifies the comment to be removed.
   *  
   *  The request body needs no data, so it should be empty.
   *  
   */
  @ApiOperation(value = "Remove a specific comment associated with this file",
      notes = "Method takes the comment id as a UUID. No arguments necessary in the request body.",
      responseClass = "None", httpMethod = "DELETE")
  def removeComment(id: UUID) = 
    SecuredAction(parse.json, authorization = WithPermission(Permission.RemoveComments)) {    
    implicit request =>
                
        var commentId: UUID = id        
        if (UUID.isValid(commentId.stringify)) {

            Logger.debug(s"removeComment from file with id  $commentId.")

            comments.removeComment(commentId)
            Ok(Json.obj("status" -> "success"))
        } 
        else {
            Logger.error(s"The given id $commentId is not a valid ObjectId.")
            BadRequest(toJson(s"The given id $commentId is not a valid ObjectId."))
        }
  }
  //End, remove comment code
  
  //Edit comment code starts
  /**
   * REST endpoint: POST: edit a comment associated with a specific file
   * 
   *  The method takes a single arg:
   *  
   *  id: A UUID that identifies the comment to be edited.
   *  
   *  The data contained in the request body will contain data for the edit, stored in the following key-value pairs:
   *  
   *  "commentText" -> The updated text to be used for the comment.
   */
  @ApiOperation(value = "Edit a specific comment associated with this file",
      notes = "Method takes the comment id as a UUID. commentText key-value pair necessary in the request body.",
      responseClass = "None", httpMethod = "POST")
  def editComment(id: UUID) = 
  SecuredAction(parse.json, authorization = WithPermission(Permission.EditComments)) {    
      implicit request =>                
      var commentId: UUID = id        
      if (UUID.isValid(commentId.stringify)) {

          //Set up the vars we are looking for
          var commentText: String = null;

          var aResult: JsResult[String] = (request.body \ "commentText").validate[String]

          // Pattern matching
          aResult match {
          case s: JsSuccess[String] => {
              commentText = s.get
          }
          case e: JsError => {
              Logger.error("Errors: " + JsError.toFlatJson(e).toString())
              BadRequest(toJson(s"description data is missing."))
          }                            
      }

      Logger.debug(s"editComment from file with id  $commentId.")

      comments.editComment(commentId, commentText)
      Ok(Json.obj("status" -> "success"))
      } 
      else {
          Logger.error(s"The given id $commentId is not a valid ObjectId.")
          BadRequest(toJson(s"The given id $commentId is not a valid ObjectId."))
      }
  }
  //End, remove comment code
  
}