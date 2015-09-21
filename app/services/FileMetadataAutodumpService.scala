package services

import play.api.{ Plugin, Logger, Application }
import play.libs.Akka
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

/**
 * File metadata automatic dump service.
 *
 * @author Constantinos Sophocleous
 *
 */
class FileMetadataAutodumpService (application: Application) extends Plugin {

  val files: FileService = DI.injector.getInstance(classOf[FileService])
  
  override def onStart() {
    Logger.debug("Starting file metadata autodumper Plugin")
    //Dump metadata of all files periodically
    val timeInterval = play.Play.application().configuration().getInt("filemetadatadump.dumpEvery") 
	Akka.system().scheduler.schedule(0.days, timeInterval.intValue().days){
	      dumpAllFileMetadata
	}    
  }
  
  override def onStop() {
    Logger.debug("Shutting down file metadata autodumper Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("filemetadatadumpservice").filter(_ == "disabled").isDefined
  }
  
  def dumpAllFileMetadata() = {
    val unsuccessfulDumps = files.dumpAllFileMetadata
    if(unsuccessfulDumps.size == 0)
      Logger.info("Dumping and staging of files metadata was successful for all files.")
    else{
      var unsuccessfulMessage = "Dumping of files metadata was successful for all files except file(s) with id(s) "
      for(badFile <- unsuccessfulDumps){
        unsuccessfulMessage = unsuccessfulMessage + badFile + ", "
      }
      unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
      Logger.info(unsuccessfulMessage)  
    } 
    
  }
  
}