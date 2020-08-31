package services

trait MailerService {
  def sendMail(subscriberMail : String, html: String, subject: String): Boolean
}
