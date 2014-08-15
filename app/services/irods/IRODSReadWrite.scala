package services.irods

import play.api.{ Play, Logger }
import play.api.Play.current
import java.io.{ IOException, InputStream }
import java.net.{ URI, URISyntaxException }

import org.irods.jargon.core.pub.io.{IRODSFile, IRODSFileOutputStream, IRODSFileInputStream}
import org.irods.jargon.core.exception.JargonException

class IRODSReadWrite() {

  val ipg = Play.application.plugin[IRODSPlugin].getOrElse(throw new RuntimeException("irods: Plugin not loaded."))
  //val ipg = current.plugin[IRODSPlugin].foreach{_.openIRODSConnection()}
  val irodsFileFactory = ipg.getFileFactory
   
  def readFile(filename:String):InputStream = {     
 
    if (!ipg.conn) {
      ipg.openIRODSConnection()
      Logger.info("irods: Connected. " + ipg.conn)
    }
    
    val filePathHome: String = ipg.userhome + IRODSFile.PATH_SEPARATOR

    Logger.debug("irods: readFile() - filePath: " + filePathHome + filename)

    try {
	  var file:IRODSFile = irodsFileFactory.instanceIRODSFile(filePathHome + filename)				  
	  Logger.debug("irods: readFile() - File exists? " + file.exists())				  
 
	  irodsFileFactory.instanceIRODSFileInputStream(file).asInstanceOf[InputStream]
      
    } catch {
    // exceptions return String, need to return null object as an is 
	  case e : IOException => Logger.error("irods: readFile() - Resource failure.." + e.toString()); return null
	  case je: JargonException => Logger.error("irods: readFile() - Error getting an IRODS stream" + je.toString()); return null
    }
   }

  def storeFile(filename:String, is:InputStream) = { 
    var fos: IRODSFileOutputStream = null
        
    if (!ipg.conn) {
      ipg.openIRODSConnection()
      Logger.info("irods: Connected. " + ipg.conn)
    }

    val filePathHome: String = ipg.userhome + IRODSFile.PATH_SEPARATOR
    var filePath: String = ""
      
    Logger.debug("irods: storeFile() - Input stream bytes: " + is.available())  //Number of bytes that can be read
    
    // fill a buffer Array
    val buffer = Array.ofDim[Byte](10240)
	is.read(buffer)
    is.close()
    
 	/*   
 	// create folder structure with levels from id string
    // DataWolf logic, if implemented we need to store the relative path in metadata

	var levels:Int = 2
	var i = 0
	while (i < 2*levels) {
	  if (id.length() >= i+2) //break
	  
	  filePath = filePathHome + id.substring(i, i+2) + IRODSFile.PATH_SEPARATOR
      i += 2
    }
    if (id.length() > 0) filePath = filePathHome + id + IRODSFile.PATH_SEPARATOR
    */
      
	// append the filename
     Option(filename) match {
      case Some(filename) if (!filename.isEmpty) => filePath = filePathHome + filename
      case _ => filePath = filePathHome + "unknown.unk"
    }
	Logger.debug("irods: storeFile - filePath: " + filePath)

	try {
	  val file = irodsFileFactory.instanceIRODSFile(filePath)
	  //file.createNewFile()
	  //file.setExecutable(true, true)
	  // create medici folder if it does not exist
	  if (!file.getParentFile().isDirectory()) {
	    if (!file.getParentFile().mkdirs()) {
		  throw new IOException("irods: storeFile() - Could not create folder to store data in.")
		}
	  }

	  var fos = irodsFileFactory.instanceIRODSFileOutputStream(file)
	  fos.write(buffer)        
             
    } catch {
	  case je: JargonException => Logger.error("irods: storeFile() - Error saving dataset to iRODS storage. " + je.toString())
	  case e : IOException => Logger.error("irods: storeFile() - Error saving dataset to iRODS storage." + e.toString())
	  case _: Throwable => Logger.error("irods: storeFile() - Error saving to iRODS storage.")
	} finally {
       if (fos != null) {
          fos.close()
       }
       // no need to close connection since upload/save is immediately followed by download/getBytes
    }
  }

  def deleteFile(filename:String): Boolean = {
    if (!ipg.conn) {
      ipg.openIRODSConnection()
      Logger.info("irods: Connected. " + ipg.conn)
    }
   try {
      val filePathHome: String = ipg.userhome + IRODSFile.PATH_SEPARATOR      
      val file: IRODSFile = irodsFileFactory.instanceIRODSFile(filePathHome + filename)
      Logger.debug("irods: deleteFile() - File exists? " + file.exists())

      file.exists() match {
        case true => file.deleteWithForceOption() match {
          case true => Logger.info("irods: The file " + filename + " has been deleted."); return true
          case false => Logger.debug("irods: deleteFile() - Error deleting a file " + filename); return false
        }
        case false => Logger.debug("irods: deleteFile() - The file " +  filename + " does not exist."); return false
      }
    } catch {
      case e: URISyntaxException => Logger.error("irods: deleteFile() - File path or filename is not correct: " + e.toString); return false
      case je: JargonException => Logger.error("irods: deleteFile() - Error accessing iRODS storage. " + je.toString); return false
    } finally {
      if (ipg.conn) {
        ipg.closeIRODSConnection()
      }
    }
  }
}