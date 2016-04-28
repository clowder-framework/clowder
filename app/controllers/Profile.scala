package controllers

import services._
import play.api.data.Form
import play.api.data.Forms._
import models._
import javax.inject.Inject
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import services.UserService
import services.mongodb.{MongoDBInstitutionService, MongoDBProjectService}

// TODO CATS-66 remove MongoDBInstitutionService, make part of UserService?
class Profile @Inject() (users: UserService, files: FileService, datasets: DatasetService, collections: CollectionService,
                         institutions: MongoDBInstitutionService, projects: MongoDBProjectService, events: EventService,
                         scheduler: SchedulerService, spaces: SpaceService) extends SecuredController {

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

  def editProfile() = AuthenticatedAction { implicit request =>
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
        Redirect(routes.Error.authenticationRequired())
      }
    }
  }


  def viewProfileUUID(uuid: UUID) = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    val viewerUser = request.user
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

        Ok(views.html.profile(existingUser, ownProfile))
        
      }
      case None => {
        Logger.error("no user model exists for " + uuid.stringify)
        BadRequest("no user model exists for " + uuid.stringify)
      }
    }
  }
                /** @deprecated use viewProfileUUID(uuid) */
  def viewProfile(email: Option[String]) = AuthenticatedAction { implicit request =>
    implicit val user = request.user

    users.findByEmail(email.getOrElse("")) match {
      case Some(user) => Redirect(routes.Profile.viewProfileUUID(user.id))
      case None => {
        Logger.error("no user model exists for " + email.getOrElse(""))
        BadRequest("no user model exists for " + email.getOrElse(""))
      }
    }
  }

  def submitChanges = AuthenticatedAction { implicit request =>
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
      case None => Redirect(routes.Error.authenticationRequired())
    }
  }
}
