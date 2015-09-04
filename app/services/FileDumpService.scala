package services

import play.api.{Plugin, Logger, Application}
import play.api.Play.current
import java.io.File
import org.apache.commons.io.FileUtils

/**
 * File dump service.
 *
 * @author Constantinos Sophocleous
 *
 */
class FileDumpService(application: Application) extends Plugin {

  var fileDumpDir: Option[String] = None
  var fileDumpMoveDir: Option[String] = None

  override def onStart() {
    Logger.debug("Starting file dumper Plugin")
    val fileSep = System.getProperty("file.separator")
    var fileDumpDir = play.api.Play.configuration.getString("filedump.dir").getOrElse("")
	if(!fileDumpDir.equals("")){
	    if (!fileDumpDir.endsWith(fileSep))
	      fileDumpDir = fileDumpDir + fileSep
	    this.fileDumpDir = Some(fileDumpDir)
		
		var fileDumpMoveDir = play.api.Play.configuration.getString("filedumpmove.dir").getOrElse("")
		if(!fileDumpMoveDir.equals("")){
			if(!fileDumpMoveDir.endsWith(fileSep))
				fileDumpMoveDir = fileDumpMoveDir + fileSep
			this.fileDumpMoveDir = Some(fileDumpMoveDir)
		}
	}	
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
        val filePathInDirs = fileDump.fileId.charAt(fileDump.fileId.length()-3)+ fileSep + fileDump.fileId.charAt(fileDump.fileId.length()-2)+fileDump.fileId.charAt(fileDump.fileId.length()-1)+ fileSep + fileDump.fileId + fileSep + fileDump.fileName
        val fileDumpingDir = dumpDir + filePathInDirs
        val copiedFile = new File(fileDumpingDir)
        FileUtils.copyFile(fileDump.fileToDump, copiedFile)
        
        fileDumpMoveDir match {
          case Some(dumpMoveDir) => {
            val fileDumpingMoveDir = dumpMoveDir + filePathInDirs
            val movedFile = new File(fileDumpingMoveDir)
            movedFile.getParentFile().mkdirs()
            
            if(copiedFile.renameTo(movedFile)){
            	Logger.info("File dumped and moved to staging directory successfully.")
            }else{
            	Logger.warn("Could not move dumped file to staging directory.")
    	    }
          }
          case None => Logger.warn("Could not move dumped file to staging directory. No staging directory set.")
        }        
      }
      case None => Logger.warn("Could not dump file. No file dumping directory set.")
    }
  }

}

case class DumpOfFile(
  fileToDump: File,
  fileId: String,
  fileName: String
)