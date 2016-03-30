package services

import javax.inject.Inject

import play.api.templates.Html
import play.api.{ Plugin, Logger, Application }
import play.api.Play.current
import models.UUID
import util.Mail

class AdminsNotifierPlugin @Inject()(userService: UserService) (application:Application) extends Plugin {
  override def onStart() {
    Logger.debug("Starting Admins Notifier Plugin")
  }
  override def onStop() {
    Logger.debug("Shutting down Admins Notifier Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("adminnotifierservice").filter(_ == "disabled").isDefined
  }
  
  def sendAdminsNotification(baseURL: String, resourceType: String = "Dataset", eventType: String = "added",
                             resourceId: String, resourceName: String) = {

    val mailSubject = resourceType + " " + eventType + ": " + resourceName
    val resourceUrl = if(resourceType.equals("File")) {
      baseURL + controllers.routes.Files.file(UUID(resourceId))
    } else if(resourceType.equals("Dataset")) {
      baseURL + controllers.routes.Datasets.dataset(UUID(resourceId))
    } else if(resourceType.equals("Collection")){
      baseURL + controllers.routes.Collections.collection(UUID(resourceId))
    }
    
    resourceUrl match{
      case "" => {
        Logger.error("Unknown resource type.")
      }
      case _=> {
        val mailHTML = if (eventType.equals("added")) {
          "The " + resourceType.toLowerCase + " is available at <a href='" + resourceUrl + "'>" + resourceUrl + "</a>"
        } else if (eventType.equals("removed")) {
          resourceType + " had id " + resourceId + "."
        } else ""

        mailHTML match {
          case "" => {
        	  Logger.error("Unknown event type.")
          }
          case _=> {
            Mail.sendEmailAdmins(mailSubject, None, Html(mailHTML))
          }
        }	
      }
    }
  }
}

