package services

import play.api.{ Plugin, Logger, Application }
import play.api.Play.current
import services._
import models.UUID

class AdminsNotifierPlugin(application:Application) extends Plugin {

  val files: FileService =  DI.injector.getInstance(classOf[FileService])
  val datasets: DatasetService =  DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService =  DI.injector.getInstance(classOf[CollectionService])
  
  val appConfiguration: AppConfigurationService = services.DI.injector.getInstance(classOf[AppConfigurationService])
  
  val hostUrl = "http://" + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port")
  
  override def onStart() {
    Logger.debug("Starting Admins Notifier Plugin")

  }
  override def onStop() {
    Logger.debug("Shutting down Admins Notifier Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("adminnotifierservice").filter(_ == "disabled").isDefined
  }
  
  def sendAdminsNotification(resourceType: String = "Dataset", eventType: String = "added", resourceId: String, resourceName: String) = {
    
    var resourceUrl = ""
    val mailSubject = resourceType + " " + eventType + ": " + resourceName
    if(resourceType.equals("File")){
      resourceUrl = hostUrl + controllers.routes.Files.file(UUID(resourceId))
    }
    else if(resourceType.equals("Dataset")){
      resourceUrl = hostUrl + controllers.routes.Datasets.dataset(UUID(resourceId))
    }
    else if(resourceType.equals("Collection")){
      resourceUrl = hostUrl + controllers.routes.Collections.collection(UUID(resourceId))
    }
    
    resourceUrl match{
      case "" =>{
        Logger.error("Unknown resource type.")
      }
      case _=>{
        var mailHTML = ""
        if(eventType.equals("added"))  
        	mailHTML = "The " + resourceType.toLowerCase() + " is available at <a href='"+resourceUrl+"'>"+resourceUrl+"</a>" 
        else if(eventType.equals("removed"))
        	mailHTML = resourceType + " had id " + resourceId + "."
        
        mailHTML match{
          case "" =>{
        	  Logger.error("Unknown event type.")
          }
          case _=>{
	        var adminsNotSent = ""
	        for(admin <- appConfiguration.getDefault.get.admins){
	          var wasSent = false
	          current.plugin[MailerPlugin].foreach{currentPlugin => {
		    	  			    		 	wasSent = wasSent || currentPlugin.sendMail(admin, mailHTML, mailSubject)}}
	          if(!wasSent)
		    	  adminsNotSent = adminsNotSent + ", " + admin
	        }
	        
	        if(adminsNotSent.equals("")){
		    	 Logger.info("Notification posted successfully.")
		    }
		    else{
		    	 Logger.info("Notification was posted to all admins but the following, for which posting failed: " + adminsNotSent.substring(2))
		    }
          }          
        }	
      }
    }
      
  }
  
}
