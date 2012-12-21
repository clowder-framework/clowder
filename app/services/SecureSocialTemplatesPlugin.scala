package services

import securesocial.controllers.TemplatesPlugin
import play.api.mvc.Request
import securesocial.controllers.Registration.RegistrationInfo
import play.api.templates.Html
import play.api.mvc.RequestHeader
import securesocial.core.SocialUser
import play.api.data.Form

class SecureSocialTemplatesPlugin(application: play.Application) extends TemplatesPlugin {
 /**
   * Returns the html for the login page
   * @param request
   * @tparam A
   * @return
   */
  override def getLoginPage[A](implicit request: Request[A], form: Form[(String, String)],
                               msg: Option[String] = None): Html = {
    views.html.ss.login(form, msg)
  }

  /**
   * Returns the html for the signup page
   *
   * @param request
   * @tparam A
   * @return
   */
  override def getSignUpPage[A](implicit request: Request[A], form: Form[RegistrationInfo], token: String): Html = {
    views.html.ss.Registration.signUp(form, token)
  }

  /**
   * Returns the html for the start signup page
   *
   * @param request
   * @tparam A
   * @return
   */
  override def getStartSignUpPage[A](implicit request: Request[A], form: Form[String]): Html = {
    views.html.ss.Registration.startSignUp(form)
  }

  /**
   * Returns the html for the reset password page
   *
   * @param request
   * @tparam A
   * @return
   */
  override def getStartResetPasswordPage[A](implicit request: Request[A], form: Form[String]): Html = {
    views.html.ss.Registration.startResetPassword(form)
  }

  /**
   * Returns the html for the start reset page
   *
   * @param request
   * @tparam A
   * @return
   */
  def getResetPasswordPage[A](implicit request: Request[A], form: Form[(String, String)], token: String): Html = {
    views.html.ss.Registration.resetPasswordPage(form, token)
  }

  /**
   * Returns the email sent when a user starts the sign up process
   *
   * @param token the token used to identify the request
   * @param request the current http request
   * @return a String with the html code for the email
   */
  def getSignUpEmail(token: String)(implicit request: RequestHeader): String = {
    views.html.ss.mails.signUpEmail(token).body
  }

  /**
   * Returns the email sent when the user is already registered
   *
   * @param user the user
   * @param request the current request
   * @return a String with the html code for the email
   */
  def getAlreadyRegisteredEmail(user: SocialUser)(implicit request: RequestHeader): String = {
    views.html.ss.mails.alreadyRegisteredEmail(user).body
  }

  /**
   * Returns the welcome email sent when the user finished the sign up process
   *
   * @param user the user
   * @param request the current request
   * @return a String with the html code for the email
   */
  def getWelcomeEmail(user: SocialUser)(implicit request: RequestHeader): String = {
    views.html.ss.mails.welcomeEmail(user).body
  }

  /**
   * Returns the email sent when a user tries to reset the password but there is no account for
   * that email address in the system
   *
   * @param request the current request
   * @return a String with the html code for the email
   */
  def getUnknownEmailNotice()(implicit request: RequestHeader): String = {
    views.html.ss.mails.unknownEmailNotice(request).body
  }

  /**
   * Returns the email sent to the user to reset the password
   *
   * @param user the user
   * @param token the token used to identify the request
   * @param request the current http request
   * @return a String with the html code for the email
   */
  def getSendPasswordResetEmail(user: SocialUser, token: String)(implicit request: RequestHeader): String = {
    views.html.ss.mails.passwordResetEmail(user, token).body
  }

  /**
   * Returns the email sent as a confirmation of a password change
   *
   * @param user the user
   * @param request the current http request
   * @return a String with the html code for the email
   */
  def getPasswordChangedNoticeEmail(user: SocialUser)(implicit request: RequestHeader): String = {
    views.html.ss.mails.passwordChangedNotice(user).body
  }
}