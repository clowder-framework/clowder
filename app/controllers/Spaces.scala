package controllers

import java.net.URL
import java.util.Date
import javax.inject.Inject

import api.Permission
import models._
import play.api.{Play, Logger}
import play.api.data.Forms._
import play.api.data.{Form, Forms}
import services.{SpaceService, UserService}
import util.Direction._
import securesocial.core.providers.{Token, UsernamePasswordProvider}
import org.joda.time.DateTime
import play.api.i18n.Messages
import services.AppConfiguration
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 */
case class spaceFormData(
  name: String,
  description: String,
  homePage: List[URL],
  logoURL: Option[URL],
  bannerURL: Option[URL],
  spaceId:Option[UUID],
  resourceTimeToLive: Long,
  isTimeToLiveEnabled: Boolean,
  submitButtonValue:String)

case class spaceInviteData(
  addresses: List[String],
  role: String,
  message: String)

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
      "editTime" -> longNumber,
      "isTimeToLiveEnabled" -> boolean,
      "submitValue" -> text
    )
      (
          (name, description, logoUrl, bannerUrl, homePages, space_id, editTime, isTimeToLiveEnabled, bvalue) => spaceFormData(name = name, description = description,
             homePage = homePages, logoURL = logoUrl, bannerURL = bannerUrl, space_id, resourceTimeToLive = editTime, isTimeToLiveEnabled = isTimeToLiveEnabled, bvalue)
        )
      (
          (d:spaceFormData) => Some(d.name, d.description, d.logoURL, d.bannerURL, d.homePage, d.spaceId, d.resourceTimeToLive, d.isTimeToLiveEnabled, d.submitButtonValue)
        )
  )

  /**
   * Invite to space form bindings.
   */
  val spaceInviteForm = Form(
    mapping(
      "addresses" -> play.api.data.Forms.list(nonEmptyText),
      "role" -> nonEmptyText,
      "message" -> nonEmptyText
      )
      (( addresses, role, message ) => spaceInviteData(addresses = addresses, role = role, message = message))
      ((d:spaceInviteData) => Some(d.addresses, d.role, d.message))
  )

  /**
   * Space main page.
   */
  def getSpace(id: UUID, size: Int, direction: String) = PermissionAction(Permission.ViewSpace, Some(ResourceRef(ResourceRef.space, id))) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
        case Some(s) => {
	        val creator = users.findById(s.creator)
	        var creatorActual: User = null
	        val collectionsInSpace = spaces.getCollectionsInSpace(Some(id.stringify), Some(direction), Some(size))
	        val datasetsInSpace = spaces.getDatasetsInSpace(Some(id.stringify), Some(direction), Some(size))
	        val usersInSpace = spaces.getUsersInSpace(id)
	        var inSpaceBuffer = usersInSpace.to[ArrayBuffer]
	        creator match {
	            case Some(theCreator) => {
	            	inSpaceBuffer += theCreator
	            	creatorActual = theCreator
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
	                    role = aRole.name
	                }
	                case None => {
	                    //This case catches spaces that have been created before users and roles were assigned to them.
	                    if (aUser == creatorActual) {
	                        role = "Admin"
	                        users.findRoleByName(role) match {
	                            case Some(realRole) => {
	                                spaces.addUser(aUser.id, realRole, id)
	                            }
	                            case None => Logger.debug("No Admin role found for some reason.")
	                        }
	                    }
	                }
	            }

	            userRoleMap += (aUser -> role)
	        }
	        //For testing. To fix back to normal, replace inSpaceBuffer.toList with usersInSpace
          var roleList: List[String] = List.empty
          users.listRoles().map{
            role => roleList = role.name :: roleList
          }
	        Ok(views.html.spaces.space(Utils.decodeSpaceElements(s), collectionsInSpace, datasetsInSpace, creator, userRoleMap, externalUsers.toList, roleList.sorted))
      }
      case None => InternalServerError("Space not found")
    }
  }

  def newSpace() = AuthenticatedAction { implicit request =>
      implicit val user = request.user
    Ok(views.html.spaces.newSpace(spaceForm))
  }

  def updateSpace(id:UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, id))) { implicit request =>
      implicit val user = request.user
      spaces.get(id) match {
        case Some(s) => {
          Ok(views.html.spaces.editSpace(spaceForm.fill(spaceFormData(s.name, s.description,s.homePage, s.logoURL, s.bannerURL, Some(s.id), s.resourceTimeToLive, s.isTimeToLiveEnabled, "Update"))))}
        case None => InternalServerError("Space not found")
      }
  }

  def invite(id:UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, id))) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
      case Some(s) => {
        val roleList: List[String] = users.listRoles().map(role => role.name)
        Ok(views.html.spaces.invite(spaceInviteForm, Utils.decodeSpaceElements(s), roleList.sorted))}
      case None => InternalServerError("Space not found")
    }
  }

  def inviteToSpace(id: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, id))) {
    implicit request =>
      implicit val user = request.user
      spaces.get(id) match {
        case Some(s) => {
          val roleList: List[String] = users.listRoles().map( role=> role.name)
          spaceInviteForm.bindFromRequest.fold(
          errors => BadRequest(views.html.spaces.invite(errors, s, roleList.sorted)),
          formData => {
            users.findRoleByName(formData.role) match {
              case Some(role) => {
                formData.addresses.map {
                  email =>
                  securesocial.core.UserService.findByEmailAndProvider(email, UsernamePasswordProvider.UsernamePassword) match {
                    case Some(member) => {
                      //Add Person to the space
                      val usr = users.findByEmail(email)
                      spaces.addUser(usr.get.id, role, id)
                      val theHtml = views.html.spaces.inviteNotificationEmail(id.stringify, s.name, user.get.getMiniUser, usr.get.fullName, role.name)
                      Users.sendEmail("Added to space", email, theHtml)
                    }
                    case None => {
                      val uuid = UUID.generate()
                      val TokenDurationKey = securesocial.controllers.Registration.TokenDurationKey
                      val DefaultDuration = securesocial.controllers.Registration.DefaultDuration
                      val TokenDuration = Play.current.configuration.getInt(TokenDurationKey).getOrElse(DefaultDuration)
                      val token = new Token(uuid.stringify, email, DateTime.now(), DateTime.now().plusMinutes(TokenDuration), true)
                      securesocial.core.UserService.save(token)
                      val invite = SpaceInvite(uuid, uuid.toString(), email, s.id, role.id.stringify)
                      if(play.api.Play.current.configuration.getBoolean("registerThroughAdmins").get)
                      {
                        val theHtml = views.html.inviteEmailThroughAdmin(uuid.stringify, email, s.name, user.get.getMiniUser.fullName, formData.message)
                        val admins = AppConfiguration.getAdmins
                        for(admin <- admins) {
                          Users.sendEmail(Messages("mails.sendSignUpEmail.subject"), admin, theHtml)
                        }
                        spaces.addInvitationToSpace(invite)
                      }
                      if(!play.api.Play.current.configuration.getBoolean("registerThroughAdmins").get)
                      {
                        val theHtml = views.html.inviteThroughEmail(uuid.stringify, s.name, user.get.getMiniUser.fullName, formData.message)
                        Users.sendEmail(Messages("mails.sendSignUpEmail.subject"), email, theHtml)
                        spaces.addInvitationToSpace(invite)
                      }
                    }
                  }
                }
                Redirect(routes.Spaces.getSpace(s.id))
              }
              case None => InternalServerError("Role not found")
            }
          }

          )
        }
        case None => InternalServerError("Space not found")
      }
  }

  /**
   * Submit action for new or edit space
   */
  // TODO this should check to see if user has editpsace for specific space
  def submit() = AuthenticatedAction { implicit request =>
      implicit val user = request.user
      user match {
        case Some(identity) => {
          val userId = request.user.get.id
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
                                                collectionCount = 0, datasetCount = 0, userCount = 0, metadata = List.empty,
                                                resourceTimeToLive = formData.resourceTimeToLive * 60 * 60 * 1000L, isTimeToLiveEnabled = formData.isTimeToLiveEnabled)

                    // insert space
                    spaces.insert(newSpace)
                    val role = Role.Admin
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
                        if (Permission.checkPermission(user, Permission.EditSpace, Some(ResourceRef(ResourceRef.space, existing_space.id)))) {
                          val updated_space = existing_space.copy(name = formData.name, description = formData.description, logoURL = formData.logoURL, bannerURL = formData.bannerURL,
                            homePage = formData.homePage, resourceTimeToLive = formData.resourceTimeToLive * 60 * 60 * 1000L, isTimeToLiveEnabled = formData.isTimeToLiveEnabled)
                          spaces.update(updated_space)
                          Redirect(routes.Spaces.getSpace(existing_space.id))
                        } else {
                          Redirect(routes.Spaces.getSpace(existing_space.id)).flashing("error" -> "You are not authorized to edit this spaces")
                        }
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
           filter: Option[String], mode: String) = PrivateServerAction { implicit request =>
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
      val deletePermission = Permission.checkPermission(user, Permission.DeleteDataset)
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
      val viewMode: Option[String] =
        if (mode == null || mode == "") {
          request.cookies.get("view-mode") match {
            case Some(cookie) => Some(cookie.value)
            case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
          }
        } else {
          Some(mode)
        }

      Ok(views.html.spaces.listSpaces(decodedSpaceList.toList, order, direction, start, limit, filter, viewMode, deletePermission, prev, next))
  }
}
