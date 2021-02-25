package services

import play.api.Logger
import play.api.mvc.{RequestHeader, Session}
import java.util.Date

import securesocial.core._
import securesocial.core.providers.UsernamePasswordProvider


class SecureSocialEventListener(app: play.api.Application) extends EventListener {
  override def id: String = "SecureSocialEventListener"
  lazy val userService: UserService = DI.injector.getInstance(classOf[UserService])
  lazy val spaceService: SpaceService = DI.injector.getInstance(classOf[SpaceService])
  lazy val sinkService: EventSinkService = DI.injector.getInstance(classOf[EventSinkService])
  lazy val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

  def onEvent(event: Event, request: RequestHeader, session: Session): Option[Session] = {
    event match {
      case e: SignUpEvent => {
        userService.findByIdentity(event.user) match {
          case Some(user) => {
            val subject = s"[${AppConfiguration.getDisplayName}] new user signup"
            val body = views.html.emails.userSignup(user)(request)
            util.Mail.sendEmailAdmins(subject, Some(user), body)
            user.email match {
              case Some(e) => spaceService.processInvitation(e)
              case None => Logger.debug("No email found for user "+user.id.stringify)
            }
            userService.updateUserField(user.id, "lastLogin", new Date())
            sinkService.logUserSignupEvent(user)
          }
          case None => {
            Logger.error(s"Could not find user ${event.user.fullName} in database")
          }
        }
      }
      case e: LoginEvent => {
        userService.findByIdentity(event.user) match {
          case Some(user) => {
            if (user.lastLogin.isEmpty && event.user.identityId.providerId != UsernamePasswordProvider.UsernamePassword) {
              val subject = s"[${AppConfiguration.getDisplayName}] new user signup"
              val body = views.html.emails.userSignup(user)(request)
              util.Mail.sendEmailAdmins(subject, Some(user), body)
              appConfig.incrementCount('users, 1)
            }
            user.email match {
              case Some(e) => spaceService.processInvitation(e)
              case None => Logger.debug("No email found for user "+user.id.stringify)
            }
            userService.updateUserField(user.id, "lastLogin", new Date())
            sinkService.logUserLoginEvent(user)
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
