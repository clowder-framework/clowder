package controllers

import api.Permission.Permission
import api.{Permission, UserRequest}
import models.{ClowderUser, RequestResource, ResourceRef, User, UserStatus}
import org.apache.commons.lang.StringEscapeUtils._
import play.api.i18n.Messages
import play.api.mvc._
import securesocial.core.{Authenticator, SecureSocial, UserService}
import services._
import securesocial.core.IdentityProvider
import securesocial.core.providers.utils.RoutesHelper

import scala.concurrent.Future

/**
 * Action builders check permissions in controller calls. When creating a new endpoint, pick one of the actions defined below.
 *
 * All functions will always resolve the usr and place the user in the request.user.
 *
 * UserAction: call the wrapped code, no checks are done
 * AuthenticatedAction: call the wrapped code iff the user is logged in.
 * ServerAdminAction: call the wrapped code iff the user is a server admin.
 * PermissionAction: call the wrapped code iff the user has the right permission on the reference object.
 *
 */
trait SecuredController extends Controller {

  val userservice = DI.injector.getInstance(classOf[services.UserService])

  /** get user if logged in */
  def UserAction(needActive: Boolean) = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      userRequest.user match {
        case Some(u) if !AppConfiguration.acceptedTermsOfServices(u.termsOfServices) => {
          if (request.uri.startsWith(routes.Application.tos().url)) {
            block(userRequest)
          } else {
            Future.successful(Results.Redirect(routes.Application.tos(Some(request.uri))))
          }
        }
        case Some(u) if needActive && (u.status==UserStatus.Inactive) => Future.successful(Results.Redirect(routes.Error.notActivated()))
        case _ => block(userRequest)
      }
    }
  }

  /**
   * Use when you want to require the user to be logged in on a private server or the server is public.
   */
  def PrivateServerAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      userRequest.user match {
        case Some(u) if !AppConfiguration.acceptedTermsOfServices(u.termsOfServices) => Future.successful(Results.Redirect(routes.Application.tos(Some(request.uri))))
        case Some(u) if (u.status==UserStatus.Inactive) => Future.successful(Results.Redirect(routes.Error.notActivated()))
        case Some(u) if u.superAdminMode || Permission.checkPrivateServer(userRequest.user) => block(userRequest)
        case None if Permission.checkPrivateServer(userRequest.user) => block(userRequest)
        case _ => Future.successful(Results.Redirect(securesocial.controllers.routes.LoginPage.login)
          .flashing("error" -> "You must be logged in to access this page.")
          .withSession(request.session + (SecureSocial.OriginalUrlKey -> request.uri)))
      }
    }
  }

  /** call code iff user is logged in */
  def AuthenticatedAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      userRequest.user match {
        case Some(u) if !AppConfiguration.acceptedTermsOfServices(u.termsOfServices) => {
          if (request.uri.startsWith(routes.Users.acceptTermsOfServices().url)) {
            block(userRequest)
          } else {
            Future.successful(Results.Redirect(routes.Application.tos(Some(request.uri))))
          }
        }
        case Some(u) if (u.status==UserStatus.Inactive) => Future.successful(Unauthorized("Account is not activated"))
        case Some(u) => block(userRequest)
        case None => Future.successful(Results.Redirect(securesocial.controllers.routes.LoginPage.login)
          .flashing("error" -> "You must be logged in to access this page.")
          .withSession(request.session + (SecureSocial.OriginalUrlKey -> request.uri)))
      }
    }
  }

  /** call code if user is a server admin */
  def ServerAdminAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      userRequest.user match {
        case Some(u) if !AppConfiguration.acceptedTermsOfServices(u.termsOfServices) => Future.successful(Results.Redirect(routes.Application.tos(Some(request.uri))))
        case Some(u) if (u.status==UserStatus.Inactive) => Future.successful(Results.Redirect(routes.Error.notActivated()))
        case Some(u) if u.superAdminMode || Permission.checkServerAdmin(userRequest.user) => block(userRequest)
        case _ => Future.successful(Results.Redirect(securesocial.controllers.routes.LoginPage.login)
          .flashing("error" -> "You must be logged in as an administrator to access this page.")
          .withSession(request.session + (SecureSocial.OriginalUrlKey -> request.uri)))
      }
    }
  }

  /** call code if user has right permission for resource */
  def PermissionAction(permission: Permission, resourceRef: Option[ResourceRef] = None) = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      val userRequest = getUser(request)
      userRequest.user match {
        case Some(u) if !AppConfiguration.acceptedTermsOfServices(u.termsOfServices) => Future.successful(Results.Redirect(routes.Application.tos(Some(request.uri))))
        case Some(u) if (u.status==UserStatus.Inactive) => Future.successful(Results.Redirect(routes.Error.notActivated()))
        case Some(u) if u.superAdminMode || Permission.checkPermission(userRequest.user, permission, resourceRef) => block(userRequest)
        case Some(u) => notAuthorizedMessage(userRequest.user, resourceRef)
        case None if Permission.checkPermission(userRequest.user, permission, resourceRef) => block(userRequest)
        // Anonymous user access to a private space
        case None if permission == Permission.ViewSpace => notAuthorizedMessage(userRequest.user, resourceRef)
        case None => Future.successful(Results.Redirect(securesocial.controllers.routes.LoginPage.login)
          .flashing("error" -> "You must be logged in to perform that action.")
          .withSession(request.session + (SecureSocial.OriginalUrlKey -> request.uri)))
      }
    }
  }

  private def notAuthorizedMessage(user: Option[User], resourceRef: Option[ResourceRef]): Future[SimpleResult] = {
    val messageNoPermission = "You are not authorized to access "

    resourceRef match {
      case None => Future.successful(Results.Redirect(routes.Error.notAuthorized("Unknown resource", "Unknown id", "no resource")))

      case Some(ResourceRef(ResourceRef.file, id)) => {
        val files: FileService = DI.injector.getInstance(classOf[FileService])
        files.get(id) match {
          case None => Future.successful(BadRequest(views.html.notFound("File does not exist.")(user)))
          case Some(file) => Future.successful(Results.Redirect(routes.Error.notAuthorized(messageNoPermission + "file \"" + file.filename + "\"", id.toString, "file")))
        }
      }

      case Some(ResourceRef(ResourceRef.dataset, id)) => {
        val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
        datasets.get(id) match {
          case None => Future.successful(BadRequest(views.html.notFound("Dataset does not exist.")(user)))
          case Some(dataset) => Future.successful(Results.Redirect(routes.Error.notAuthorized(messageNoPermission
            + "dataset \"" + dataset.name + "\"", id.toString, "dataset")))
        }
      }

      case Some(ResourceRef(ResourceRef.collection, id)) => {
        val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
        collections.get(id) match {
          case None => Future.successful(BadRequest(views.html.notFound("Collection does not exist.")(user)))
          case Some(collection) => Future.successful(Results.Redirect(routes.Error.notAuthorized(messageNoPermission
            + "collection \"" + collection.name + "\"", id.toString, "collection")))
        }
      }

      case Some(ResourceRef(ResourceRef.space, id)) => {
        val spaceTitle: String = Messages("space.title")
        val spaces: SpaceService = DI.injector.getInstance(classOf[SpaceService])
        spaces.get(id) match {
          case None => Future.successful(BadRequest(views.html.notFound(spaceTitle + " does not exist.")(user)))
          case Some(space) => Future.successful(Forbidden(views.html.spaces.space(space,List(),List(),List(),List(),"", Map(),List())(user)))
        }
      }

      case Some(ResourceRef(ResourceRef.curationObject, id)) =>{
        val curations: CurationService = DI.injector.getInstance(classOf[CurationService])
        curations.get(id) match {
          case None =>  Future.successful(BadRequest(views.html.notFound("Publication Request does not exist.")(user)))
          case Some(curation) => Future.successful(Results.Redirect(routes.Error.notAuthorized(messageNoPermission
            + "publication request \"" + curation.name + "\"", id.toString(), "curation")))
        }
      }

      case Some(ResourceRef(ResourceRef.section, id)) =>{
        val sections: SectionService = DI.injector.getInstance(classOf[SectionService])
        sections.get(id) match {
          case None => Future.successful(BadRequest(views.html.notFound("Section does not exist.")(user)))
          case Some(section) => Future.successful(Results.Redirect(routes.Error.notAuthorized(messageNoPermission
            + " section \"" + section.id + "\"", id.toString(), "section")))
        }
      }

      case Some(ResourceRef(resType, id)) => {
        Future.successful(Results.Redirect(routes.Error.notAuthorized("error resource", id.toString(), resType.toString())))
      }
    }
  }

  /**
   * Disable a route without having to comment out the entry in the routes file. Useful for when we want to keep the
   * code around but we don't want users to have access to it.
   */
  def DisabledAction = new ActionBuilder[UserRequest] {
    def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[SimpleResult]) = {
      Future.successful(Results.Redirect(routes.Error.notAuthorized("", null, null)))
    }
  }

  /** Return user based on request object */
  def getUser[A](request: Request[A]): UserRequest[A] = {
    // controllers will check for user in the following order:
    // 1) secure social
    // 2) anonymous access

    val superAdmin = request.cookies.get("superAdmin").exists(_.value.toBoolean)

    // 1) secure social, this allows the web app to make calls to the API and use the secure social user
    for (
      authenticator <- SecureSocial.authenticatorFromRequest(request);
      identity <- UserService.find(authenticator.identityId)
    ) yield {
      Authenticator.save(authenticator.touch)
      val user = DI.injector.getInstance(classOf[services.UserService]).findByIdentity(identity) match {
        case Some(u: ClowderUser) if Permission.checkServerAdmin(Some(u)) => Some(u.copy(superAdminMode=superAdmin))
        case Some(u) => Some(u)
        case None => None
      }

      // find or create an api key for extractor submissions
      user match {
        case Some(u) =>
          val apiKey = userservice.getExtractionApiKey(u.identityId)
          return UserRequest(user, request, Some(apiKey.key))
        case None =>
          return UserRequest(None, request, None)
      }
    }

    // 2) anonymous access
    UserRequest(None, request, None)
  }
}
