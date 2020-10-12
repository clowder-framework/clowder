package services

import javax.inject.Inject
import models.UUID
import play.api.{Configuration, Logger}
import play.twirl.api.Html
import util.Mail

class AdminsNotifierService @Inject()(userService: UserService, config: Configuration)() {

  val enabled = {
    if (config.has("adminnotifierservice"))
      !(config.get[String]("adminnotifierservice") == "disabled")
    else
      false
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

