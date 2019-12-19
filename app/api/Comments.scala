package api

import play.api.libs.json._
import play.api.libs.json.Json._
import play.Logger
import java.util.Date
import play.api.Play.current
import javax.inject.Inject
import models._
import services._
import play.api.i18n.Messages



/**
 * Comments on datasets.
 *
 */
class Comments @Inject()(datasets: DatasetService, comments: CommentService, events: EventService, users: UserService) extends ApiController {

  def comment(id: UUID) = PermissionAction(Permission.AddComment, Some(ResourceRef(ResourceRef.comment, id)))(parse.json) { implicit request =>
      Logger.trace("Adding comment")
      comments.get(id) match {
        case Some(parent) => {
          request.user match {
            case Some(identity) => {
              request.body.\("text").asOpt[String] match {
                case Some(text) => {
                  val comment = Comment(comment_id = Some(id),
                                        author = identity,
                                        text = text,
                                        posted = new Date(),
                                        dataset_id = parent.dataset_id,
                                        file_id = parent.file_id,
                                        section_id = parent.section_id)
                  comments.insert(comment)
                  if (parent.dataset_id.isDefined) {
                    datasets.get(parent.dataset_id.get) match {
                      case Some(dataset) => {
                        current.plugin[ElasticsearchPlugin].foreach {
                          _.index(dataset, false)
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
   *  Only the owner of a comment will be allowed to delete it. Any other request will fail.
   *
   */
  def removeComment(id: UUID) = PermissionAction(Permission.DeleteComment, Some(ResourceRef(ResourceRef.comment, id)))(parse.json) { implicit request =>
      request.user match {
          case Some(identity) => {
              var commentId: UUID = id
                      if (UUID.isValid(commentId.stringify)) {

                          Logger.debug(s"removeComment from file with id  $commentId.")
                          //Check to make sure user email matches the comment email
                          comments.get(commentId) match {
                               case Some(theComment) => {
                                     //Check to make sure the user is the same as the author, otherwise, they
                                     //shouldn't be able to delete the comment.
                                     if (identity.email == theComment.author.email) {
                                         comments.removeComment(commentId)
                                         Ok(Json.obj("status" -> "success"))
                                     }
                                     else {
                                         Logger.error(s"Only the ${Messages("owner").toLowerCase()} can delete the comment.")
                                         BadRequest(toJson(s"Only ${Messages("owner").toLowerCase()} can delete the comment."))
                                     }
                               }
                               case None => {
                                     //Really shouldn't happen
                                     BadRequest(toJson("Error getting the comment."))
                               }
                          }
                      }
                      else {
                          Logger.error(s"The given id $commentId is not a valid ObjectId.")
                          BadRequest(toJson(s"The given id $commentId is not a valid ObjectId."))
                      }
          }
          case None => {
               //This case shouldn't happen, as there are checks to prevent this API from being
               //called without an Identity
               BadRequest
          }
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
   *
   *  Only the owner of the comment will be allowed to edit it. Other requests will fail.
   *
   */
  def editComment(id: UUID) = PermissionAction(Permission.EditComment, Some(ResourceRef(ResourceRef.comment, id)))(parse.json) { implicit request =>
      request.user match {
           case Some(identity) => {
               var commentId: UUID = id
               if (UUID.isValid(commentId.stringify)) {
                   Logger.debug(s"editComment from file with id  $commentId.")

                   comments.get(commentId) match {
                         case Some(theComment) => {
                             //Make sure that the author of the comment is the one editing it
                             if (identity.email == theComment.author.email) {
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
                                 events.addObjectEvent(request.user, commentId, commentText, EventType.EDIT_COMMENT.toString)
                                 Ok(Json.obj("status" -> "success"))
                             }
                             else {
                                 Logger.error(s"Only the ${Messages("owner").toLowerCase()} can edit the comment.")
                                 BadRequest(toJson(s"Only ${Messages("owner").toLowerCase()} can edit the comment."))
                             }
                         }
                         case None => {
                             //Shouldn't happen
                             BadRequest(toJson("Error getting the comment"))
                         }
                   }
               }
               else {
                   Logger.error(s"The given id $commentId is not a valid ObjectId.")
                   BadRequest(toJson(s"The given id $commentId is not a valid ObjectId."))
               }
           }
           case None => {
               //This case shouldn't happen, as there are checks to prevent this API from being
               //called without an Identity
               BadRequest
           }
      }
  }
  //End, remove comment code

    /**
        * This will create an event in the specified user's feed indicating they were mentioned in a comment
        * on the specified resource.
    */
    def mentionInComment(userid: UUID, resourceID: UUID, resourceName: String, resourceType: String, commenterId: UUID) =
        PermissionAction(Permission.AddComment, Some(ResourceRef(Symbol(resourceType), resourceID))) {
            users.get(commenterId) match {
                case Some(u) => {
                    events.addRequestEvent(users.get(userid), u, resourceID, resourceName, "mention_" + resourceType + "_comment")
                    Ok(s"Mention event added to user id $userid's feed")
                }
                case None => {
                    events.addObjectEvent(users.get(userid), resourceID, resourceName, "mention_" + resourceType + "_comment")
                    Ok(s"Mention event added to user id $userid's feed")
                }
            }

    }
}
