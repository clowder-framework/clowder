package controllers

import api.Permission.Permission
import api.{Permission, UserRequest}
import models.{ClowderUser, ResourceRef, User, UserStatus}
import play.api.i18n.Messages
import play.api.mvc._
import services._

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
trait SecuredController extends BaseController with play.api.i18n.I18nSupport {

  val messages = DI.injector.getInstance(classOf[Messages])



  /** Return user based on request object */
  def getUser[A](request: Request[A]): UserRequest[A] = {
    // controllers will check for user in the following order:
    // 1) secure social
    // 2) anonymous access

    val superAdmin = request.cookies.get("superAdmin").exists(_.value.toBoolean)

    // 2) anonymous access
    UserRequest(None, request, None)
  }
}
