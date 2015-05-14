package controllers

import api.{Permission, WithPermission}
import services.{ UserService, FileService, DatasetService, CollectionService, EventService }
import services.mongodb.MongoDBProjectService
import services.mongodb.MongoDBInstitutionService
import services._
import play.api.data.Form
import play.api.data.Forms._
import models._
import play.api.Logger
import javax.inject.Inject

class Profile @Inject() (users: UserService, files: FileService, datasets: DatasetService, collections: CollectionService, institutions: MongoDBInstitutionService, projects: MongoDBProjectService, events: EventService, scheduler: SchedulerService) extends SecuredController {

  val bioForm = Form(
    mapping(
      "avatarUrl" -> optional(text),
      "biography" -> optional(text),
      "currentprojects" -> list(text),
      "institution" -> optional(text),
      "orcidID" -> optional(text),
      "pastprojects" -> list(text),
      "position" -> optional(text),
      "emailsettings" -> optional(text)
    )(Profile.apply)(Profile.unapply)
  )

  def editProfile() = SecuredAction(authorization = WithPermission(Permission.LoggedIn)) { implicit request =>
    implicit val user = request.user

    user match {
      case Some(muser) => {
        val newbioForm = bioForm.fill(muser.profile.getOrElse(new models.Profile()))
        var allProjectOptions: List[String] = "" :: projects.getAllProjects()
        var allInstitutionOptions: List[String] = "" :: institutions.getAllInstitutions()
        var emailtimes: List[String] = List("daily", "hourly", "weekly", "none")
        Ok(views.html.editProfile(newbioForm, allInstitutionOptions, allProjectOptions, emailtimes))
      }
      case None => {
        Redirect(routes.RedirectUtility.authenticationRequired())
      }
    }
  }

  def viewProfileUUID(uuid: UUID) = SecuredAction() { request =>
    implicit val user = request.user
    val viewerUser = request.user
    var followers: List[(UUID, String, String, String)] = List.empty
    var followedUsers: List[(UUID, String, String, String)] = List.empty
    var followedFiles: List[(UUID, String, String)] = List.empty
    var followedDatasets: List[(UUID, String, String)] = List.empty
    var followedCollections: List[(UUID, String, String)] = List.empty
    var myFiles : List[(UUID, String, String)] = List.empty
    var myDatasets: List[(UUID, String, String)] = List.empty
    var myCollections: List[(UUID, String, String)] = List.empty
    var maxDescLength = 50
    var ownProfile: Option[Boolean] = None
    var muser = users.findById(uuid)
    
    muser match {
      case Some(existingUser) => {
        viewerUser match {
          case Some(loggedInUser) => {
            if (loggedInUser.id == existingUser.id)
              ownProfile = Option(true)
            else
              ownProfile = None
          }
          case None => {
            ownProfile = None
          }
        }
        
        for (tidObject <- existingUser.followedEntities) {
              if (tidObject.objectType == "user") {
                var followedUser = users.get(tidObject.id)
                followedUser match {
                  case Some(fuser) => {
                    followedUsers = followedUsers.++(List((fuser.id, fuser.fullName, fuser.email.get, fuser.getAvatarUrl())))
                  }
                }
              } else if (tidObject.objectType == "file") {
                var followedFile = files.get(tidObject.id)
                followedFile match {
                  case Some(ffile) => {
                    followedFiles = followedFiles.++(List((ffile.id, ffile.filename, ffile.contentType)))
                  }
                }
              } else if (tidObject.objectType == "dataset") {
                var followedDataset = datasets.get(tidObject.id)
                followedDataset match {
                  case Some(fdset) => {
                    followedDatasets = followedDatasets.++(List((fdset.id, fdset.name, fdset.description.substring(0, Math.min(maxDescLength, fdset.description.length())))))
                  }
                }
              } else if (tidObject.objectType == "collection") {
                var followedCollection = collections.get(tidObject.id)
                followedCollection match {
                  case Some(fcoll) => {
                    followedCollections = followedCollections.++(List((fcoll.id, fcoll.name, fcoll.description.substring(0, Math.min(maxDescLength, fcoll.description.length())))))
                  }
                }
              }
            }

            for (followerID <- existingUser.followers) {
              var userFollower = users.findById(followerID)
              userFollower match {
                case Some(uFollower) => {
                  var ufEmail = uFollower.email
                  ufEmail match {
                    case Some(fufEmail) => {
                      followers = followers.++(List((uFollower.id, fufEmail, uFollower.getAvatarUrl(), uFollower.fullName)))
                    }
                  }   
                }
              }
            }

            existingUser.email match {
              case Some(addr) => {
                var userDatasets: List[Dataset] = datasets.listUserDatasetsAfter("", 12, addr.toString())
                var userCollections: List[Collection] = collections.listUserCollectionsAfter("", 12, addr.toString())
                var userFiles : List[File] = files.listUserFilesAfter("", 12, addr.toString())
                
                for (dset <- userDatasets) {
                  myDatasets = myDatasets.++(List((dset.id, dset.name, dset.description.substring(0, Math.min(maxDescLength, dset.description.length())))))
                }
                for (cset <- userCollections) {
                  myCollections = myCollections.++(List((cset.id, cset.name, cset.description.substring(0, Math.min(maxDescLength, cset.description.length())))))
                }
                for (fset <- userFiles) {
                  myFiles = myFiles.++(List((fset.id, fset.filename, fset.contentType)))
                }
              }
            }
            Ok(views.html.profile(existingUser, ownProfile, followers, followedUsers, followedFiles, followedDatasets, followedCollections, myFiles, myDatasets, myCollections))
        
      }
      case None => {
        Logger.error("no user model exists for " + uuid.stringify)
        InternalServerError
      }
    }
  }

  /** @deprecated use viewProfileUUID(uuid) */
  def viewProfile(email: Option[String]) = SecuredAction() { request =>
    implicit val user = request.user

    users.findByEmail(email.getOrElse("")) match {
      case Some(user) => Redirect(routes.Profile.viewProfileUUID(user.id))
      case None => {
        Logger.error("no user model exists for " + email.getOrElse(""))
        InternalServerError
      }
    }
  }

  def submitChanges = SecuredAction(authorization = WithPermission(Permission.LoggedIn)) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(x: User) => {
        bioForm.bindFromRequest.fold(
          errors => BadRequest(views.html.editProfile(errors, List.empty, List.empty, List.empty)),
          profile => {
            users.updateProfile(x.id, profile)

            profile.emailsettings match {
              case Some(setting) => {
                scheduler.updateEmailJob(x.id, "Digest[" + x.id + "]", setting)
              }
              case None => {
                scheduler.deleteJob("Digest[" + x.id + "]")
              }
            }
            Redirect(routes.Profile.viewProfileUUID(x.id))
          }
        )
      }
      case None => Redirect(routes.RedirectUtility.authenticationRequired())
    }
  }
}
