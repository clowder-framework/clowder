package services

import play.api.{ Plugin, Logger, Application }
import  play.api.Play.current
import java.io.File
import org.apache.commons.io.FileUtils

/**
 * File dump service.
 *
 * @author Constantinos Sophocleous
 *
 */
class FileDumpService (application: Application) extends Plugin {
  
  var fileDumpDir: Option[String] = None

  override def onStart() {
    Logger.debug("Starting file dumper Plugin")
    
    val fileSep = System.getProperty("file.separator")
	var fileDumpDir = play.api.Play.configuration.getString("filedump.dir").getOrElse("")
	if(!fileDumpDir.endsWith(fileSep))
		fileDumpDir = fileDumpDir + fileSep
	this.fileDumpDir = Some(fileDumpDir) 
  }
  
  override def onStop() {
    Logger.debug("Shutting down file dumper Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("filedumpservice").filter(_ == "disabled").isDefined
  }
  
  def dump(fileDump: DumpOfFile) = {
    Logger.debug("Dumping file " + fileDump.fileName)
    fileDumpDir match {
      case Some(dumpDir) => {
        val fileSep = System.getProperty("file.separator")
        val fileDumpingDir = dumpDir + fileDump.fileId.charAt(fileDump.fileId.length()-3)+ fileSep + fileDump.fileId.charAt(fileDump.fileId.length()-2)+fileDump.fileId.charAt(fileDump.fileId.length()-1)+ fileSep + fileDump.fileId + fileSep + fileDump.fileName        
        FileUtils.copyFile(fileDump.fileToDump, new File(fileDumpingDir))
      }
      case None => Logger.warn("Could not dump file.")
    }
  }
 
}

case class DumpOfFile (
    fileToDump: File,
    fileId: String,
    fileName: String
)