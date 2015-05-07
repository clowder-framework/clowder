package controllers

import java.net.URL
import javax.inject.Inject
import api.{Permission, WithPermission}
import models.{Dataset, Collection, ProjectSpace, UUID}
import play.api.Logger
import play.api.data.{Forms, Form}
import play.api.data.Forms._
import java.util.Date
import services.{UserService, SpaceService}
import util.Direction._
import org.apache.commons.lang.StringEscapeUtils
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ArrayBuffer
import models.Role
import models.User

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 *
 * @author Luigi Marini
 * @author Rob Kooper
 *
 */
case class spaceFormData(
  name: String,
  description: String,
  homePage: List[URL],
  logoURL: Option[URL],
  bannerURL: Option[URL],
  spaceId:Option[UUID],
  submitButtonValue:String)

class Spaces @Inject()(spaces: SpaceService, users: UserService) extends SecuredController {

  /**
   * New/Edit project space form bindings.
   */
  val spaceForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText,
      "logoUrl" -> optional(Utils.CustomMappings.urlType),
      "bannerUrl" -> optional(Utils.CustomMappings.urlType),
      "homePages" -> Forms.list(Utils.CustomMappings.urlType),
      "space_id" -> optional(Utils.CustomMappings.uuidType),
      "submitValue" -> text
    )
      (
          (name, description, logoUrl, bannerUrl, homePages, space_id, bvalue) => spaceFormData(name = name, description = description,
             homePage = homePages, logoURL = logoUrl, bannerURL = bannerUrl, space_id, bvalue)
        )
      (
          (d:spaceFormData) => Some(d.name, d.description, d.logoURL, d.bannerURL, d.homePage, d.spaceId, d.submitButtonValue)
        )
  )


  /**
   * Space main page.
   */
  def getSpace(id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowSpace)) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
        case Some(s) => {
	        val creator = users.findById(s.creator)
	
	        val collectionsInSpace = spaces.getCollectionsInSpace(id)
	
	        val datasetsInSpace = spaces.getDatasetsInSpace(id)
	        
	        val usersInSpace = spaces.getUsersInSpace(id)
	        var inSpaceBuffer = usersInSpace.to[ArrayBuffer]
	        creator match {
	            case Some(theCreator) => {
	            	inSpaceBuffer += theCreator
	            }
	            case None => Logger.debug("-------- No creator for space found...")
	        }
	        
	        
	        var externalUsers = users.list.to[ArrayBuffer] 
	        
	        //inSpaceBuffer += externalUsers(0)
	        externalUsers --= inSpaceBuffer
	
	        var userRoleMap: Map[User, String] = Map.empty
	        for (aUser <- inSpaceBuffer) {
	            var role = "What"
	            spaces.getRoleForUserInSpace(id, aUser.id) match {
	                case Some(aRole) => {
	                    Logger.debug("-------- Match found at level " + aRole)
	                    role = aRole.name
	                }
	                case None => {
	                    Logger.debug("-------- No match")
	                }
	            }
	            userRoleMap += (aUser -> role)
	        }
	        //For testing. To fix back to normal, replace inSpaceBuffer.toList with usersInSpace
	        Ok(views.html.spaces.space(Utils.decodeSpaceElements(s), collectionsInSpace, datasetsInSpace, creator, userRoleMap, externalUsers.toList, Role.roleList))
      }
      case None => InternalServerError("Space not found")
    }
  }

  def newSpace() = SecuredAction(authorization = WithPermission(Permission.CreateSpaces)) {
    implicit request =>
      implicit val user = request.user
    Ok(views.html.spaces.newSpace(spaceForm))
  }

  def updateSpace(id:UUID) = SecuredAction(authorization = WithPermission(Permission.EditSpace)) {
    implicit request =>
      implicit val user = request.user
      spaces.get(id) match {
        case Some(s) => {
          Ok(views.html.spaces.editSpace(spaceForm.fill(spaceFormData(s.name, s.description,s.homePage, s.logoURL, s.bannerURL, Some(s.id), "Update"))))}
        case None => InternalServerError("Space not found")
      }
    }
  /**
   * Submit action for new or edit space
   */
  def submit() = SecuredAction(parse.anyContent) {
    implicit request =>
      implicit val user = request.user
      user match {
        case Some(identity) => {
          val userId = request.mediciUser.fold(UUID.generate)(_.id)
          //need to get the submitValue before binding form data, in case of errors we want to trigger different forms
          request.body.asMultipartFormData.get.dataParts.get("submitValue").headOption match {
            case Some(x) => {
              x(0) match {
              case ("Create") => {
                spaceForm.bindFromRequest.fold(
                  errors => BadRequest(views.html.spaces.newSpace(errors)),
                  formData => {
                    Logger.debug("Creating space " + formData.name)
                    val newSpace = ProjectSpace(name = formData.name, description = formData.description,
                                                created = new Date, creator = userId, homePage = formData.homePage,
                                                logoURL = formData.logoURL, bannerURL = formData.bannerURL,
                                                collectionCount = 0, datasetCount = 0, userCount = 0, metadata = List.empty)
                    // insert space
                    spaces.insert(newSpace)
                    val role = Role(name = "Admin")
                    spaces.addUser(userId, role, newSpace.id)
                    //TODO - Put Spaces in Elastic Search?
                    // index collection
                    // val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
                    //current.plugin[ElasticsearchPlugin].foreach{_.index("data", "collection", collection.id,
                    // Notify admins a new space is created
                    //  List(("name",collection.name), ("description", collection.description), ("created",dateFormat.format(new Date()))))}
                    //current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification(Utils.baseUrl(request), "Space","added",space.id.toString,space.name)}
                    // redirect to space page
                     Redirect(routes.Spaces.getSpace(newSpace.id))
                  })
              }
              case ("Update") => {
                spaceForm.bindFromRequest.fold(
                  errors => BadRequest(views.html.spaces.editSpace(errors)),
                  formData => {
                    Logger.debug("updating space " + formData.name)
                    spaces.get(formData.spaceId.get) match {
                      case Some(existing_space) => {
                        val updated_space = existing_space.copy(name = formData.name, description = formData.description, logoURL = formData.logoURL, bannerURL = formData.bannerURL, homePage = formData.homePage)
                        spaces.update(updated_space)
                        Redirect(routes.Spaces.getSpace(existing_space.id))
                      }
                      case None => {
                        BadRequest("The space does not exist")
                      }
                    }
                  })
              }
              case _ => {BadRequest("Do not recognize the submit button value.")}

              }
              }
            case None => {BadRequest("Did not get any submit button value.")}
            }
        } //some identity
        case None => Redirect(routes.Spaces.list()).flashing("error" -> "You are not authorized to create/edit spaces.")
      }
  }

   /**
   * Show the list page
   */
  def list(order: Option[String], direction: String, start: Option[String], limit: Int,
           filter: Option[String], mode: String) =
    SecuredAction(authorization = WithPermission(Permission.ListSpaces)) {
    implicit request =>
      implicit val user = request.user
      val d = if (direction.toLowerCase.startsWith("a")) {
        ASC
      } else if (direction.toLowerCase.startsWith("d")) {
        DESC
      } else if (direction == "1") {
        ASC
      } else if (direction == "-1") {
        DESC
      } else {
        ASC
      }
      val spaceList = spaces.list(order, d, start, limit, filter)
      var decodedSpaceList = new ListBuffer[models.ProjectSpace]()
      for (aSpace <- spaceList) {
        decodedSpaceList += Utils.decodeSpaceElements(aSpace)
      }
      val deletePermission = WithPermission(Permission.DeleteDatasets).isAuthorized(user)
      val prev = if (decodedSpaceList.size > 0) {
        spaces.getPrev(order, d, decodedSpaceList.head.created, limit, filter).getOrElse("")
      } else {
        ""
      }
      val next = if (decodedSpaceList.size > 0) {
        spaces.getNext(order, d, decodedSpaceList.last.created, limit, filter).getOrElse("")
      } else {
        ""
      }
      //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
      //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar
      //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14
      var viewMode = mode;
      //Always check to see if there is a session value
      request.cookies.get("view-mode") match {
        case Some(cookie) => {
          viewMode = cookie.value
        }
        case None => {
          //If there is no cookie, and a mode was not passed in, default it to tile
          if (viewMode == null || viewMode == "") {
            viewMode = "tile"
          }
        }
      }

      Ok(views.html.spaces.listSpaces(decodedSpaceList.toList, order, direction, start, limit, filter, viewMode, deletePermission, prev, next))
  }
}
