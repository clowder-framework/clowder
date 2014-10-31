package services

import play.api.{ Plugin, Logger, Application }
import play.api.Play.current
import services._
import models.UUID

class AdminsNotifierPlugin(application:Application) extends Plugin {

  val appConfiguration: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

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
          "The " + resourceType.toLowerCase() + " is available at <a href='" + resourceUrl + "'>" + resourceUrl + "</a>"
        } else if (eventType.equals("removed")) {
          resourceType + " had id " + resourceId + "."
        } else ""

        mailHTML match {
          case "" => {
        	  Logger.error("Unknown event type.")
          }
          case _=> {
	          var adminsNotSent = ""
	          for(admin <- appConfiguration.getDefault.get.admins) {
	            var wasSent = false
	            current.plugin[MailerPlugin].foreach{currentPlugin => {
		    	      wasSent = wasSent || currentPlugin.sendMail(admin, mailHTML, mailSubject)}}
	            if(!wasSent) adminsNotSent = adminsNotSent + ", " + admin
	          }
	        
  	        if(adminsNotSent.equals("")) {
		    	    Logger.info("Notification posted successfully.")
		        } else {
		    	    Logger.info("Notification was posted to all admins but the following, for which posting failed: " +
                adminsNotSent.substring(2))
		        }
          }          
        }	
      }
    }
  }

}

