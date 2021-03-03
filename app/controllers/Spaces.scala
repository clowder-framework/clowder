package controllers

import java.net.URL
import java.util.{ Calendar, Date }
import javax.inject.Inject

import api.Permission
import api.Permission._
import models._
import play.api.{ Logger, Play }
import play.api.data.Forms._
import play.api.data.{ Form, Forms }
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.i18n.Messages
import services._
import securesocial.core.providers.{ Token, UsernamePasswordProvider }
import org.joda.time.DateTime
import play.api.i18n.Messages
import play.api.libs.ws._
import services.AppConfiguration
import util.{ Formatters, Mail, Publications }

import scala.collection.immutable.List
import scala.collection.mutable.{ ArrayBuffer, ListBuffer }
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._
import org.apache.commons.lang.StringEscapeUtils.escapeJava

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 */
case class spaceFormData(
  name: String,
  description: String,
  homePage: List[URL],
  logoURL: Option[URL],
  bannerURL: Option[URL],
  spaceId: Option[UUID],
  resourceTimeToLive: Long,
  isTimeToLiveEnabled: Boolean,
  access: String,
  affSpace: List[String],
  submitButtonValue: String)

case class spaceInviteData(
  addresses: List[String],
  role: String,
  message: Option[String])

class Spaces @Inject() (spaces: SpaceService, users: UserService, events: EventService, curationService: CurationService,
  extractors: ExtractorService, datasets: DatasetService, collections: CollectionService, selections: SelectionService, sinkService: EventSinkService) extends SecuredController {

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
      "access" -> nonEmptyText,
      "affSpaces" -> Forms.list(nonEmptyText),
      "submitValue" -> text)(
        (name, description, logoUrl, bannerUrl, homePages, space_id, editTime, isTimeToLiveEnabled, access, affSpaces, bvalue) => spaceFormData(name = name, description = description,
          homePage = homePages, logoURL = logoUrl, bannerURL = bannerUrl, space_id, resourceTimeToLive = editTime, isTimeToLiveEnabled = isTimeToLiveEnabled, access = access, affSpace=affSpaces, bvalue))(
          (d: spaceFormData) => Some(d.name, d.description, d.logoURL, d.bannerURL, d.homePage, d.spaceId, d.resourceTimeToLive, d.isTimeToLiveEnabled, d.access, d.affSpace, d.submitButtonValue)))

  /**
   * Invite to space form bindings. we are not using play.api.data.Forms.list(email) for addresses since the constraints
   * of front-end and back-end are different.
   */
  val spaceInviteForm = Form(
    mapping(
      "addresses" -> play.api.data.Forms.list(nonEmptyText),
      "role" -> nonEmptyText,
      "message" -> optional(text))((addresses, role, message) => spaceInviteData(addresses = addresses, role = role, message = message))((d: spaceInviteData) => Some(d.addresses, d.role, d.message)))

  /**
   * String name of the Space such as 'Project space' etc., parsed from conf/messages
   */
  val spaceTitle: String = Messages("space.title")

  /**
   * Gets list of extractors from mongo. Displays the page to enable/disable extractors.
   */
  def selectExtractors(id: UUID) = AuthenticatedAction {
    implicit request =>
      implicit val user = request.user
      spaces.get(id) match {
        case Some(s) => {
          // get list of registered extractors
          val runningExtractors: List[ExtractorInfo] = extractors.listExtractorsInfo(List.empty)
          // list of extractors enabled globally
          val globalSelections: List[String] = extractors.getEnabledExtractors()
          // get list of extractors registered with a specific space
          val selectedExtractors: Option[ExtractorsForSpace] = spaces.getAllExtractors(id)
          val (enabledInSpace, disabledInSpace) = spaces.getAllExtractors(id) match {
            case Some(extractorsForSpace) => (extractorsForSpace.enabled, extractorsForSpace.disabled)
            case None => (List.empty[String], List.empty[String])
          }
          Ok(views.html.spaces.updateExtractors(runningExtractors, enabledInSpace, disabledInSpace, globalSelections, id, s.name))
        }
        case None => InternalServerError(spaceTitle + " not found")
      }
  }

  /**
   * Processes POST request. Updates list of extractors associated with this space in mongo.
   */
  def updateExtractors(id: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, id)))(parse.multipartFormData) {
    implicit request =>
      implicit val user = request.user
      // form contains space id and list of extractors.
      var space_id: String = ""
      var extractors: List[String] = Nil

      val dataParts = request.body.dataParts
      if (!dataParts.isDefinedAt("space_id")) {
        Logger.error("space id not defined")
        BadRequest(spaceTitle + " id not defined")
      } else {
        // space id passed as hidden parameter
        space_id = dataParts("space_id").head
        spaces.get(new UUID(space_id)) match {
          case Some(existing_space) => {
            // FIXME by splitting the operation in two separate queries we run into transaction issues if there is
            //  a hickup between the two. We should try to do the two db queries in one.

            // 1. remove entry with extractors for this space from mongo
            spaces.deleteAllExtractors(existing_space.id)
            // 2. if extractors are selected, add them
            val prefix = "extractors-"
            extractors = dataParts.keysIterator.filter(_.startsWith(prefix)).toList
            extractors.foreach { extractor =>
              // get the first entry and ignore all others (there should only be one)
              val name = extractor.substring(prefix.length)
              val value = dataParts(extractor)(0)
              if (value.equals("default")) {
                spaces.setDefaultExtractor(existing_space.id, name)
              } else if (value.equals("enabled")) {
                spaces.enableExtractor(existing_space.id, name)
               } else if (value.equals("disabled")) {
                spaces.disableExtractor(existing_space.id, name)
              } else {
                Logger.error("Wrong value for update space extractor form")
              }
            }
//            if (dataParts.isDefinedAt("extractors-override")) {
//              extractors = dataParts("extractors-override").toList
//              extractors.map(spaces.disableExtractor(existing_space.id, _))
//            }
            Redirect(routes.Spaces.getSpace(new UUID(space_id)))
          }
          case None => {
            BadRequest("The " + spaceTitle + " does not exist")
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
        val datasetsInSpace = datasets.listSpace(size, id.toString(), user)
        val publicDatasetsInSpace = datasets.listSpaceStatus(size, id.toString(), "publicAll", user)
        val usersInSpace = spaces.getUsersInSpace(id, None)
        var curationObjectsInSpace: List[CurationObject] = List()
        var inSpaceBuffer = usersInSpace.to[ArrayBuffer]
        creator match {
          case Some(theCreator) => {
            inSpaceBuffer += theCreator
            creatorActual = theCreator
          }
          case None => Logger.error(s" No creator for $spaceTitle $id found...")
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

        Logger.debug("User selections" + user)
        val userSelections: List[String] =
          if (user.isDefined) selections.get(user.get.identityId.userId).map(_.id.stringify)
          else List.empty[String]
        Logger.debug("User selection " + userSelections)
        
        val rs = play.api.Play.current.plugin[services.StagingAreaPlugin] match {
          case Some(plugin) => Publications.getPublications(s.id.toString, spaces)
          case None => List.empty
        }
        sinkService.logSpaceViewEvent(s, user)
        Ok(views.html.spaces.space(Utils.decodeSpaceElements(s), collectionsInSpace, publicDatasetsInSpace, datasetsInSpace, rs, play.Play.application().configuration().getString("SEADservices.uri"), userRoleMap, userSelections))
      }
      case None => BadRequest(views.html.notFound(spaceTitle + " does not exist."))
    }
  }

  def newSpace() = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    Ok(views.html.spaces.newSpace(spaceForm))
  }

  def updateSpace(id: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, id))) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
      case Some(s) => {
        Ok(views.html.spaces.editSpace(spaceForm.fill(spaceFormData(s.name, s.description, s.homePage, s.logoURL, s.bannerURL, Some(s.id), s.resourceTimeToLive, s.isTimeToLiveEnabled, s.status, s.affiliatedSpaces, "Update")), Some(s.id), Some(s.name)))
      }
      case None => BadRequest(views.html.notFound(spaceTitle + " does not exist."))
    }
  }

  def manageUsers(id: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, id))) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
      case Some(s) => {
        val creator = users.findById(s.creator)
        var creatorActual: User = null
        val usersInSpace = spaces.getUsersInSpace(id, None)
        var inSpaceBuffer = usersInSpace.to[ArrayBuffer]
        creator match {
          case Some(theCreator) => {
            inSpaceBuffer += theCreator
            creatorActual = theCreator
          }
          case None => Logger.error(s" No creator for " + spaceTitle + " $id found...")
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
        users.listRoles().map {
          role => roleList = role.name :: roleList
        }
        //get the list of invitation, and also change the role from Role.id to Role.name
        val inviteBySpace = spaces.getInvitationBySpace(s.id) map (v => v.copy(role = users.findRole(v.role) match {
          case Some(r) => r.name
          case _ => "Undefined Role"
        }))

        //correct space.userCount according to usersInSpace.length
        spaces.updateUserCount(s.id, usersInSpace.length)
        val roleDescription = users.listRoles() map (t => t.name -> t.description) toMap

        Ok(views.html.spaces.users(spaceInviteForm, Utils.decodeSpaceElements(s), creator, userRoleMap, externalUsers.toList, roleList.sorted, inviteBySpace, roleDescription))
      }
      case None => BadRequest(views.html.notFound(spaceTitle + " does not exist."))
    }
  }

  def inviteToSpace(id: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, id))) {
    implicit request =>
      implicit val user = request.user
      spaces.get(id) match {
        case Some(s) => {
          val roleList: List[String] = users.listRoles().map(role => role.name)
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
                      Mail.sendEmail(s"[${AppConfiguration.getDisplayName}] - Added to $spaceTitle", request.user, email, theHtml)
                        }
                        case None => {
                          val uuid = UUID.generate()
                          val TokenDurationKey = securesocial.controllers.Registration.TokenDurationKey
                          val DefaultDuration = securesocial.controllers.Registration.DefaultDuration
                          val TokenDuration = Play.current.configuration.getInt(TokenDurationKey).getOrElse(DefaultDuration)
                          val token = new Token(uuid.stringify, email, DateTime.now(), DateTime.now().plusMinutes(TokenDuration), true)
                          securesocial.core.UserService.save(token)
                          val ONE_MINUTE_IN_MILLIS = 60000
                          val date: Calendar = Calendar.getInstance()
                          val t = date.getTimeInMillis()
                          val afterAddingMins: Date = new Date(t + (TokenDuration * ONE_MINUTE_IN_MILLIS))
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
            })
        }
        case None => BadRequest(views.html.notFound(spaceTitle + " does not exist."))
      }
  }

  /**
   * Each user with EditSpace permission will see the request on index and receive an email.
   */
  def addRequest(id: UUID) = AuthenticatedAction { implicit request =>
    implicit val requestuser = request.user
    requestuser match {
      case Some(user) => {
        spaces.get(id) match {
          case Some(s) => {
            // when permission is public, user can reach the authorization request button, so we check if the request is
            // already inserted
            if (s.requests.contains(RequestResource(user.id))) {
              Ok(views.html.authorizationMessage("Your prior request for " + spaceTitle + " " + s.name + " is active, and pending", s))
            } else if (spaces.getRoleForUserInSpace(s.id, user.id) != None) {
              Ok(views.html.authorizationMessage("You are already part of the " + spaceTitle + " " + s.name, s))
            } else {
              Logger.debug("Request submitted in controller.Space.addRequest  ")
              val subject: String = "Request for access from " + AppConfiguration.getDisplayName
              val body = views.html.spaces.requestemail(user, id.toString, s.name)

              for (requestReceiver <- spaces.getUsersInSpace(s.id, None)) {
                spaces.getRoleForUserInSpace(s.id, requestReceiver.id) match {
                  case Some(aRole) => {
                    if (aRole.permissions.contains(Permission.EditSpace.toString)) {
                      events.addRequestEvent(Some(user), requestReceiver, id, s.name, "postrequest_space")
                      Mail.sendEmail(subject, request.user, requestReceiver, body)
                    }
                  }
                }
              }
              spaces.addRequest(id, user.id, user.fullName)
              Ok(views.html.authorizationMessage("Request submitted for " + spaceTitle + " " + s.name, s))
            }
          }
          case None => InternalServerError(spaceTitle + " not found")
        }
      }

      case None => InternalServerError("User not found")
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
                    if (Permission.checkPermission(user, Permission.CreateSpace)) {
                      Logger.debug("Creating space " + formData.name)
                      val newSpace = ProjectSpace(name = formData.name, description = formData.description,
                        created = new Date, creator = userId, homePage = formData.homePage,
                        logoURL = formData.logoURL, bannerURL = formData.bannerURL,
                        collectionCount = 0, datasetCount = 0, userCount = 0, metadata = List.empty,
                        resourceTimeToLive = formData.resourceTimeToLive * 60 * 60 * 1000L, isTimeToLiveEnabled = formData.isTimeToLiveEnabled,
                        status = formData.access,
                        affiliatedSpaces = formData.affSpace)

                      // insert space
                      spaces.insert(newSpace)
                      val option_user = users.findByIdentity(identity)
                      events.addObjectEvent(option_user, newSpace.id, newSpace.name, "create_space")
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

                      //Add default metadata to metadata for the space.
                      val clowder_metadata = metadatas.getDefinitions()
                      clowder_metadata.foreach { md =>
                        val new_metadata = MetadataDefinition(spaceId = Some(newSpace.id), json = md.json)
                        metadatas.addDefinition(new_metadata)
                      }
                      Redirect(routes.Spaces.getSpace(newSpace.id))
                    } else { BadRequest("Unauthorized.") }
                  })
              }
              case ("Update") => {
                spaceForm.bindFromRequest.fold(
                  errors => BadRequest(views.html.spaces.editSpace(errors, None, None)),
                  formData => {
                    Logger.debug("updating space " + formData.name)
                    spaces.get(formData.spaceId.get) match {
                      case Some(existing_space) => {
                        if (Permission.checkPermission(user, Permission.EditSpace, Some(ResourceRef(ResourceRef.space, existing_space.id)))) {
                          val updated_space =
                            // status can only be changed by user who has PublicSpace permission.
                            Permission.checkPermission(user, Permission.PublicSpace, Some(ResourceRef(ResourceRef.space, existing_space.id))) match {
                              case true =>
                                existing_space.copy(name = formData.name, description = formData.description, logoURL = formData.logoURL, bannerURL = formData.bannerURL,
                                  homePage = formData.homePage, resourceTimeToLive = formData.resourceTimeToLive * 60 * 60 * 1000L, isTimeToLiveEnabled = formData.isTimeToLiveEnabled, status = formData.access, affiliatedSpaces = formData.affSpace)
                              case false =>
                                existing_space.copy(name = formData.name, description = formData.description, logoURL = formData.logoURL, bannerURL = formData.bannerURL,
                                  homePage = formData.homePage, resourceTimeToLive = formData.resourceTimeToLive * 60 * 60 * 1000L, isTimeToLiveEnabled = formData.isTimeToLiveEnabled, affiliatedSpaces = formData.affSpace)
                            }
                          spaces.update(updated_space)
                          val option_user = users.findByIdentity(identity)
                          events.addObjectEvent(option_user, updated_space.id, updated_space.name, "update_space_information")
                          Redirect(routes.Spaces.getSpace(existing_space.id))
                        } else {
                          Redirect(routes.Spaces.getSpace(existing_space.id)).flashing("error" -> "You are not authorized to edit this $spaceTitle.")
                        }
                      }
                      case None => {
                        BadRequest("The " + spaceTitle + " does not exist")
                      }
                    }
                  })
              }
              case _ => { BadRequest("Do not recognize the submit button value.") }

            }
          }
          case None => { BadRequest("Did not get any submit button value.") }
        }
      } //some identity
      case None => Redirect(routes.Spaces.list()).flashing("error" -> "You are not authorized to create/edit $spaceTitle.")
    }
  }
  def followingSpaces(index: Int, limit: Int, mode: String) = PrivateServerAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(clowderUser) => {
        val title: Option[String] = Some(Messages("following.title", Messages("spaces.title")))

        var spaceList = new ListBuffer[ProjectSpace]()
        val spaceIds = clowderUser.followedEntities.filter(_.objectType == "'space")
        val spaceIdsToUse = spaceIds.slice(index * limit, (index + 1) * limit)
        val prev = index - 1
        val next = if (spaceIds.length > (index + 1) * limit) {
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
  def list(when: String, date: String, limit: Int, mode: String, owner: Option[String], showAll: Boolean, showPublic: Boolean, onlyTrial: Boolean, showOnlyShared: Boolean) = UserAction(needActive = true) { implicit request =>
    implicit val user = request.user

    val nextPage = (when == "a")
    val person = owner.flatMap(o => users.get(UUID(o)))
    val ownerName = person match {
      case Some(p) => Some(p.fullName)
      case None => None
    }
    var title: Option[String] = Some(Messages("list.title", Messages("spaces.title")))

    val spaceList = person match {
      case Some(p) => {
        title = Some(Messages("owner.title", p.fullName, Messages("spaces.title")))
        if (date != "") {
          spaces.listUser(date, nextPage, limit, request.user, showAll, p)
        } else {
          spaces.listUser(limit, request.user, showAll, p)
        }
      }
      case None => {
        val trialValue = onlyTrial && Permission.checkServerAdmin(user)
        if (trialValue) {
          title = Some(Messages("trial.title", Messages("spaces.title")))
        }
        if (date != "") {
          spaces.listAccess(date, nextPage, limit, Set[Permission](Permission.ViewSpace), request.user, showAll, showPublic, trialValue, showOnlyShared)
        } else {
          spaces.listAccess(limit, Set[Permission](Permission.ViewSpace), request.user, showAll, showPublic, trialValue, showOnlyShared)
        }

      }
    }

    // check to see if there is a prev page
    val prev = if (spaceList.nonEmpty && date != "") {
      val first = Formatters.iso8601(spaceList.head.created)
      val space = person match {
        case Some(p) => spaces.listUser(first, nextPage = false, 1, request.user, showAll, p)
        case None => spaces.listAccess(first, nextPage = false, 1, Set[Permission](Permission.ViewSpace), request.user, showAll, showPublic, onlyTrial, showOnlyShared)
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
        case Some(p) => spaces.listUser(last, nextPage = true, 1, request.user, showAll, p)
        case None => spaces.listAccess(last, nextPage = true, 1, Set[Permission](Permission.ViewSpace), request.user, showAll, showPublic, onlyTrial, showOnlyShared)
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
    if (!showPublic) {
      title = Some(Messages("you.title", Messages("spaces.title")))
    }

    Ok(views.html.spaces.listSpaces(decodedSpaceList, when, date, limit, owner, ownerName, showAll, viewMode, prev, next, title, showPublic, onlyTrial))
  }

  def stagingArea(id: UUID, index: Int, limit: Int) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, id))) {
    implicit request =>
      implicit val user = request.user
      spaces.get(id) match {
        case Some(s) => {
          val curationIds = s.curationObjects.reverse.slice(index * limit, (index + 1) * limit)
          val curationObjects: List[CurationObject] = curationIds.map { curObject => curationService.get(curObject) }.flatten

          val prev = index - 1
          val next = if (s.curationObjects.length > (index + 1) * limit) {
            index + 1
          } else {
            -1
          }
          Ok(views.html.spaces.stagingarea(s, curationObjects, prev, next, limit))
        }
        case None => BadRequest(views.html.notFound(spaceTitle + " does not exist."))
      }
  }

}
