package controllers

import java.net.URL
import java.util.{Calendar, Date}
import javax.inject.Inject
import api.Permission
import api.Permission._
import models._
import play.api.{Play, Logger}
import play.api.data.Forms._
import play.api.data.{Form, Forms}
import play.api.libs.json.Json
import services._
import securesocial.core.providers.{Token, UsernamePasswordProvider}
import org.joda.time.DateTime
import play.api.i18n.Messages
import services.AppConfiguration
import util.{Mail, Formatters}
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
  message: Option[String])

class Spaces @Inject()(spaces: SpaceService, users: UserService, events: EventService, curationService: CurationService,
  extractors: ExtractorService) extends SecuredController {

  /**
   * New/Edit project space form bindings.
   */
  val spaceForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> text,
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
   * Invite to space form bindings. we are not using play.api.data.Forms.list(email) for addresses since the constraints
   * of front-end and back-end are different.
   */
  val spaceInviteForm = Form(
    mapping(
      "addresses" -> play.api.data.Forms.list(nonEmptyText),
      "role" -> nonEmptyText,
      "message" -> optional(text)
      )
      (( addresses, role, message ) => spaceInviteData(addresses = addresses, role = role, message = message))
      ((d:spaceInviteData) => Some(d.addresses, d.role, d.message))
  )

  /**
   * Gets list of extractors from mongo. Displays the page to add/remove extractors.
   */
   def selectExtractors(id:UUID) = AuthenticatedAction {
    implicit request =>
      implicit val user = request.user
      spaces.get(id) match {
        case Some(s) => {
          val runningExtractors: List[String] = extractors.getExtractorNames()
          val selectedExtractors: List[String] = spaces.getAllExtractors(id)
          Ok(views.html.spaces.updateExtractors(runningExtractors, selectedExtractors, id.stringify))
        }
        case None => InternalServerError("Space not found")      
    }
  }

  /**
   * Processes POST request. Updates list of extractors associated with this space in mongo.
   */
  def updateExtractors() = PermissionAction(Permission.EditSpace)(parse.multipartFormData) {
    implicit request =>
      implicit val user = request.user
      //form contains space id and list of extractors.
      var space_id: String = ""
      var extractors: List[String] = Nil

      val dataParts = request.body.dataParts
      if (!dataParts.isDefinedAt("space_id")) {
        Logger.error("space id not defined")
        BadRequest("Space id not defined")
      } else {
        //space id passed as hidden parameter
        space_id = dataParts("space_id").head
        spaces.get(new UUID(space_id)) match {
          case Some(existing_space) => {
            //1. remove entry with extractors for this space from mongo
            spaces.deleteAllExtractors(existing_space.id)
            //2. if extractors are selected, add them 
            if (dataParts.isDefinedAt("extractors")) {
              extractors = dataParts("extractors").toList
              extractors.map(spaces.addExtractor(existing_space.id, _))
            }
            Redirect(routes.Spaces.getSpace(new UUID(space_id)))
          }
          case None => {
            BadRequest("The space does not exist")
          }
        }

      }
  }
  
                      
                      
  
  /**
   * Space main page.
   */
  def getSpace(id: UUID, size: Int, direction: String) = PermissionAction(Permission.ViewSpace, Some(ResourceRef(ResourceRef.space, id))) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
        case Some(s) => {
	        val creator = users.findById(s.creator)
	        var creatorActual: User = null
	        val collectionsInSpace = spaces.getCollectionsInSpace(Some(id.stringify), Some(size))
	        val datasetsInSpace = spaces.getDatasetsInSpace(Some(id.stringify), Some(size))
	        val usersInSpace = spaces.getUsersInSpace(id)
	        var inSpaceBuffer = usersInSpace.to[ArrayBuffer]
	        creator match {
	            case Some(theCreator) => {
	            	inSpaceBuffer += theCreator
	            	creatorActual = theCreator
	            }
	            case None => Logger.debug("-------- No creator for space found...")
	        }

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

	        Ok(views.html.spaces.space(Utils.decodeSpaceElements(s), collectionsInSpace, datasetsInSpace, userRoleMap))
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
          Ok(views.html.spaces.editSpace(spaceForm.fill(spaceFormData(s.name, s.description,s.homePage, s.logoURL, s.bannerURL, Some(s.id), s.resourceTimeToLive, s.isTimeToLiveEnabled, "Update")), Some(s.id)))}
        case None => InternalServerError("Space not found")
      }
  }

  def manageUsers(id: UUID) = PermissionAction(Permission.EditUser, Some(ResourceRef(ResourceRef.space, id))) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
      case Some(s) => {
        val creator = users.findById(s.creator)
        var creatorActual: User = null
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
        //get the list of invitation, and also change the role from Role.id to Role.name
        val inviteBySpace = spaces.getInvitationBySpace(s.id) map(v => v.copy(role = users.findRole(v.role) match {
          case Some(r) => r.name
          case _ => "Undefined Role"
        }
          ))

        //correct space.userCount according to usersInSpace.length
        spaces.updateUserCount(s.id,usersInSpace.length)

        Ok(views.html.spaces.users(spaceInviteForm, Utils.decodeSpaceElements(s), creator, userRoleMap, externalUsers.toList, roleList.sorted, inviteBySpace))
      }
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
          errors => InternalServerError(errors.toString()),
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
                      Mail.sendEmail("Added to space", request.user, email, theHtml)
                    }
                    case None => {
                      val uuid = UUID.generate()
                      val TokenDurationKey = securesocial.controllers.Registration.TokenDurationKey
                      val DefaultDuration = securesocial.controllers.Registration.DefaultDuration
                      val TokenDuration = Play.current.configuration.getInt(TokenDurationKey).getOrElse(DefaultDuration)
                      val token = new Token(uuid.stringify, email, DateTime.now(), DateTime.now().plusMinutes(TokenDuration), true)
                      securesocial.core.UserService.save(token)
                      val ONE_MINUTE_IN_MILLIS=60000
                      val date: Calendar = Calendar.getInstance()
                      val t= date.getTimeInMillis()
                      val afterAddingMins: Date=new Date(t + (TokenDuration * ONE_MINUTE_IN_MILLIS))
                      val invite = SpaceInvite(uuid, uuid.toString(), email, s.id, role.id.stringify, new Date(), afterAddingMins)
                      val theHtml = views.html.inviteThroughEmail(uuid.stringify, s.name, user.get.getMiniUser.fullName, formData.message)
                      Mail.sendEmail(Messages("mails.sendSignUpEmail.subject"), request.user, email, theHtml)
                      spaces.addInvitationToSpace(invite)
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
   * Each user with EditSpace permission will see the request on index and receive an email.
   */
   def addRequest(id: UUID) = UserAction(needActive = true) { implicit request =>
      implicit val requestuser = request.user

    requestuser match{
      case Some(user) =>  {    spaces.get(id) match {
        case Some(s) => {
          // when permission is public, user can reach the authorization request button, so we check if the request is
          // already inserted
          if(s.requests.contains(RequestResource(user.id))) {
            Ok(views.html.authorizationMessage("Your prior request is active, and pending"))
          }else{
            Logger.debug("Request submitted in controller.Space.addRequest  ")
            val subject: String = "Request for access from " + AppConfiguration.getDisplayName
            val body = views.html.spaces.requestemail(user, id.toString, s.name)

            for (requestReceiver <- spaces.getUsersInSpace(s.id)) {
              spaces.getRoleForUserInSpace(s.id, requestReceiver.id) match {
                case Some(aRole) => {
                  if (aRole.permissions.contains("EditSpace")) {
                    events.addRequestEvent(Some(user), requestReceiver, id, s.name, "postrequest_space")

                    //sending emails to the space's Admin && Editor
                    val recipient: String = requestReceiver.email.get.toString
                    Mail.sendEmail(subject, request.user, recipient, body)
                  }
                }
              }
            }
            spaces.addRequest(id, user.id, user.fullName)
            Ok(views.html.authorizationMessage("Request submitted"))
          }
        }
        case None => InternalServerError("Space not found")
      }
    }

    case None => InternalServerError("User not found")
       }
    }

  /**
   * accept authorization request with specific Role. Send email to request user.
   */
  def acceptRequest( id:UUID, requestuser:String, role:String) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, id))) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
      case Some(s) => {
        Logger.debug("request submitted in controllers.Space.acceptrequest ")
        users.get(UUID(requestuser)) match {
          case Some(requestUser) => {
            events.addRequestEvent(user, requestUser, id, s.name, "acceptrequest_space")
            spaces.removeRequest(id, requestUser.id)
            users.findRoleByName(role) match {
              case Some(r) => spaces.addUser(requestUser.id, r, id)
              case _ => Logger.debug("Role not found" + role)
            }

            val subject: String = "Authorization Request from " + AppConfiguration.getDisplayName + " Accepted"
            val recipient: String = requestUser.email.get.toString
            val body = views.html.spaces.requestresponseemail(user.get, id.toString, s.name, "accepted your request and assigned you as " + role + " to")
            Mail.sendEmail(subject, request.user, recipient, body)
            Ok(Json.obj("status" -> "success"))
          }
          case None => InternalServerError("Request user not found")
        }
      }
      case None => InternalServerError("Space not found")
    }
  }

  def rejectRequest( id:UUID, requestuser:String) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, id))) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
      case Some(s) => {
        Logger.debug("request submitted in controller.Space.rejectRequest")
        users.get(UUID(requestuser)) match {
          case Some(requestUser) => {
            events.addRequestEvent(user, requestUser, id, spaces.get(id).get.name, "rejectrequest_space")
            spaces.removeRequest(id, requestUser.id)
            val subject: String = "Authorization Request from " + AppConfiguration.getDisplayName + " Rejected"
            val recipient: String = requestUser.email.get.toString
            val body = views.html.spaces.requestresponseemail(user.get, id.toString, s.name, "rejected your request to")
            Mail.sendEmail(subject, request.user, recipient, body)
            Ok(Json.obj("status" -> "success"))
          }
          case None => InternalServerError("Request user not found")
        }
      }
      case None => InternalServerError("Space not found")
    }
  }


  /**
   * Submit action for new or edit space
   */
  // TODO this should check to see if user has editspace for specific space
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
                  errors => BadRequest(views.html.spaces.editSpace(errors, None)),
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
  def followingSpaces(index: Int, limit: Int, mode: String) = PrivateServerAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(clowderUser) => {
        val title: Option[String] = Some("Following Spaces")

        var spaceList = new ListBuffer[ProjectSpace]()
        val spaceIds = clowderUser.followedEntities.filter(_.objectType == "'space")
        val spaceIdsToUse = spaceIds.slice(index*limit, (index+1)*limit)
        val prev=  index -1
        val next = if(spaceIds.length > (index+1) * limit) {
          index + 1
        } else {
          -1
        }
        for (tidObject <- spaceIdsToUse) {
          val followedSpace = spaces.get(tidObject.id)
          followedSpace match {
            case Some(fspace) => {
              spaceList += fspace
            }
            case None =>
          }
        }

        val decodedSpaceList = spaceList.map(Utils.decodeSpaceElements)
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

        Ok(views.html.users.followingSpaces(decodedSpaceList.toList, "", limit, None, true, viewMode, prev, next, title))

      }
      case None => InternalServerError("User not found")
    }
      }

   /**
   * Show the list page
   */
   def list(when: String, date: String, limit: Int, mode: String, owner: Option[String], showAll: Boolean) = PrivateServerAction { implicit request =>
     implicit val user = request.user

     val nextPage = (when == "a")
     val person = owner.flatMap(o => users.get(UUID(o)))
     var title: Option[String] = Some("Spaces")

     val spaceList = person match {
       case Some(p) => {
         title = Some(person.get.fullName + "'s Space")
         if (date != "") {
           spaces.listUser(date, nextPage, limit, request.user, showAll, p)
         } else {
           spaces.listUser(limit, request.user, showAll, p)
         }
       }
       case None => {
         if (date != "") {
           spaces.listAccess(date, nextPage, limit, Set[Permission](Permission.ViewSpace), request.user, showAll)
         } else {
           spaces.listAccess(limit, Set[Permission](Permission.ViewSpace), request.user, showAll)
         }
       }
     }

     // check to see if there is a prev page
     val prev = if (spaceList.nonEmpty && date != "") {
       val first = Formatters.iso8601(spaceList.head.created)
       val space = person match {
         case Some(p) => spaces.listUser(first, nextPage=false, 1, request.user, showAll, p)
         case None => spaces.listAccess(first, nextPage = false, 1, Set[Permission](Permission.ViewSpace), request.user, showAll)
       }
       if (space.nonEmpty && space.head.id != spaceList.head.id) {
         first
       } else {
         ""
       }
     } else {
       ""
     }

     // check to see if there is a next page
     val next = if (spaceList.nonEmpty) {
       val last = Formatters.iso8601(spaceList.last.created)
       val ds = person match {
         case Some(p) => spaces.listUser(last, nextPage=true, 1, request.user, showAll, p)
         case None => spaces.listAccess(last, nextPage=true, 1, Set[Permission](Permission.ViewSpace), request.user, showAll)
       }
       if (ds.nonEmpty && ds.head.id != spaceList.last.id) {
         last
       } else {
         ""
       }
     } else {
       ""
     }

     val decodedSpaceList = spaceList.map(Utils.decodeSpaceElements)
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

     Ok(views.html.spaces.listSpaces(decodedSpaceList, when, date, limit, owner, showAll, viewMode, prev, next, title))
   }


  def stagingArea(id: UUID, index: Int, limit: Int) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, id))) {
    implicit request =>
      implicit val user  = request.user
      spaces.get(id) match {
        case Some(s) => {
          val curationIds = s.curationObjects.slice(index*limit, (index+1)*limit)
          val curationDatasets: List[CurationObject] = curationIds.map{curObject => curationService.get(curObject)}.flatten

          val prev = index-1
          val next = if(s.curationObjects.length > (index+1) * limit) {
            index + 1
          } else {
            -1
          }
          Ok(views.html.spaces.stagingarea(s, curationDatasets, prev, next, limit ))
        }
        case None => InternalServerError("Space Not found")
      }
  }


}
