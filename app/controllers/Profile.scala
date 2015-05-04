package controllers

import services.{ UserService, FileService, DatasetService, CollectionService, EventService }
import services.mongodb.MongoDBProjectService
import services.mongodb.MongoDBInstitutionService
import services.mongodb.MongoDBEventService
import play.api.data.Form
import play.api.data.Forms._
import models.{ User, Info, UUID, File, Dataset, Collection, MiniUser, Event }
import play.api.Logger
import javax.inject.Inject
import models.TypedID
import java.text.SimpleDateFormat
import java.util.Date

class Profile @Inject() (users: UserService, files: FileService, datasets: DatasetService, collections: CollectionService, institutions: MongoDBInstitutionService, projects: MongoDBProjectService, events: EventService) extends SecuredController {


  val bioForm = Form(
    mapping(
      "avatarUrl" -> optional(text),
      "biography" -> optional(text),
      "currentprojects" -> list(text),
      "institution" -> optional(text),
      "orcidID" -> optional(text),
      "pastprojects" -> list(text),
      "position" -> optional(text))(Info.apply)(Info.unapply))

  def editProfile() = SecuredAction() {
    implicit request =>
      implicit val user = request.user
      var avatarUrl: Option[String] = None
      var biography: Option[String] = None
      var currentprojects: List[String] = List.empty
      var institution: Option[String] = None
      var orcidID: Option[String] = None
      var pastprojects: List[String] = List.empty
      var position: Option[String] = None
      user match {
        case Some(muser) => {
          muser.avatarUrl match {
            case Some(url) => {
              val questionMarkIdx: Int = url.indexOf("?")
              if (questionMarkIdx > -1) {
                avatarUrl = Option(url.substring(0, questionMarkIdx))
              } else {
                avatarUrl = Option(url)
              }
            }
            case None => avatarUrl = None
          }
          muser.biography match {
            case Some(filledOut) => biography = Option(filledOut)
            case None => biography = None
          }
          muser.currentprojects match {
            case x :: xs => currentprojects = x :: xs
            case nil => currentprojects = nil
          }
          muser.institution match {
            case Some(filledOut) => institution = Option(filledOut)
            case None => institution = None
          }
          muser.orcidID match {
            case Some(filledOut) => orcidID = Option(filledOut)
            case None => orcidID = None
          }
          muser.pastprojects match {
            case x :: xs => pastprojects = x :: xs
            case nil => pastprojects = nil
          }
          muser.position match {
            case Some(filledOut) => position = Option(filledOut)
            case None => position = None
          }

          val newbioForm = bioForm.fill(Info(
            avatarUrl,
            biography,
            currentprojects,
            institution,
            orcidID,
            pastprojects,
            position))
          var allProjectOptions: List[String] = projects.getAllProjects()
          var allInstitutionOptions: List[String] = institutions.getAllInstitutions()
          Ok(views.html.editProfile(newbioForm, allInstitutionOptions, allProjectOptions))
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
                    followedUsers = followedUsers.++(List((fuser.id, fuser.fullName, fuser.email.get, fuser.getAvatarUrl)))
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
                      followers = followers.++(List((uFollower.id, fufEmail, uFollower.getAvatarUrl, uFollower.fullName)))   
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

  def viewProfile(email: Option[String]) = SecuredAction() { request =>
    implicit val user = request.user
    var ownProfile: Option[Boolean] = None
    var followers: List[(UUID, String, String, String)] = List.empty
    var followedUsers: List[(UUID, String, String, String)] = List.empty
    var followedFiles: List[(UUID, String, String)] = List.empty
    var followedDatasets: List[(UUID, String, String)] = List.empty
    var followedCollections: List[(UUID, String, String)] = List.empty
    var myFiles : List[(UUID, String, String)] = List.empty
    var myDatasets: List[(UUID, String, String)] = List.empty
    var myCollections: List[(UUID, String, String)] = List.empty
    var maxDescLength = 50

    email match {
      case Some(addr) => {
        val modeluser = users.findByEmail(addr.toString())
        modeluser match {
          case Some(muser) => {
            user match {
              case Some(loggedIn) => {
                loggedIn.email match {
                  case Some(loggedEmail) => {
                    if (loggedEmail.toString == addr.toString())
                      ownProfile = Option(true)
                    else
                      ownProfile = None
                  }
                }
              }
              case None => { ownProfile = None }
            }

            for (tidObject <- muser.followedEntities) {
              if (tidObject.objectType == "user") {
                var followedUser = users.get(tidObject.id)
                followedUser match {
                  case Some(fuser) => {
                    followedUsers = followedUsers.++(List((fuser.id, fuser.fullName, fuser.email.get, fuser.getAvatarUrl)))
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

            for (followerID <- muser.followers) {
              var userFollower = users.findById(followerID)
              userFollower match {
                case Some(uFollower) => {
                  var ufEmail = uFollower.email
                  ufEmail match {
                    case Some(fufEmail) => {
                      followers = followers.++(List((uFollower.id, fufEmail, uFollower.getAvatarUrl, uFollower.fullName)))   
                    }
                  }   
                }
              }
            }

            muser.email match {
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
            Ok(views.html.profile(muser, ownProfile, followers, followedUsers, followedFiles, followedDatasets, followedCollections, myFiles, myDatasets, myCollections))
          }
          case None => {
            Logger.error("no user model exists for " + addr.toString())
            InternalServerError
          }
        }

      }
      case None => {
        user match {
          case Some(loggedInUser) => {
            Redirect(routes.Profile.viewProfile(loggedInUser.email))
          }
          case None => {
            Redirect(routes.RedirectUtility.authenticationRequired())
          }
        }
      }
    }
  }

  def submitChanges = SecuredAction() { implicit request =>
    implicit val user = request.user
    bioForm.bindFromRequest.fold(
      errors => BadRequest(views.html.editProfile(errors, List.empty, List.empty)),
      form => {
        user match {
          case Some(x) => {
            print(x.email.toString())
            implicit val email = x.email
            email match {
              case Some(addr) => {
                implicit val modeluser = users.findByEmail(addr.toString())
                users.updateUserField(addr.toString(), "avatarUrl", form.avatarUrl)
                users.updateUserField(addr.toString(), "biography", form.biography)
                users.updateUserField(addr.toString(), "currentprojects", form.currentprojects)
                users.updateUserField(addr.toString(), "institution", form.institution)
                users.updateUserField(addr.toString(), "orcidID", form.orcidID)
                users.updateUserField(addr.toString(), "pastprojects", form.pastprojects)
                users.updateUserField(addr.toString(), "position", form.position)    
                events.addUserEvent(modeluser, "edit_profile")
                Redirect(routes.Profile.viewProfile(email))
              }
            }
          } 
          case None => {
          Redirect(routes.RedirectUtility.authenticationRequired())
          }
        }
      })
  }

}
