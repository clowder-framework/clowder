package services

import play.api.Logger
import play.api.mvc.{Session, RequestHeader}
import securesocial.core._

class SecureSocialEventListener(app: play.api.Application) extends EventListener {
  override def id: String = "SecureSocialEventListener"

  def onEvent(event: Event, request: RequestHeader, session: Session): Option[Session] = {
    val userService: UserService = DI.injector.getInstance(classOf[UserService])

    event match {
      case e: SignUpEvent => {
        userService.findByIdentity(event.user) match {
          case Some(user) => {
            val subject = s"[${AppConfiguration.getDisplayName}] new user signup"
            val body = views.html.emails.userSignup(user)(request)
            util.Mail.sendEmailAdmins(subject, Some(user), body)
          }
          case None => {
            Logger.error(s"Could not find user ${event.user.fullName} in database")
          }
        }
      }
      case _ => {}
    }
    None
  }
}
