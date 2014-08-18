package services.irods

import play.api.{ Play, Logger }
import play.api.Play.current
import java.io.{ IOException, InputStream }
import java.net.{ URI, URISyntaxException }

import org.irods.jargon.core.pub.io.{IRODSFile, IRODSFileOutputStream, IRODSFileInputStream}
import org.irods.jargon.core.exception.JargonException

/**
 * Read, delete from and write files to the iRODS repository.
 * The number of levels is used when storing files to prevent too many files per
 * folder.
 * 
 * @author Chris Navarro <cmnavarr@illinois.edu>
 * @author Michal Ondrejcek <ondrejce@illinois.edu>
 * 
 */
class IRODSReadWrite() {

  val ipg = Play.application.plugin[IRODSPlugin].getOrElse(throw new RuntimeException("irods: Plugin not loaded."))
  //val ipg = current.plugin[IRODSPlugin].foreach{_.openIRODSConnection()}
  val irodsFileFactory = ipg.getFileFactory

  /** Reads file from IRODS repository using IRODSFIleFactory with instance of IRODSFile. Calls 
   *  folderStructure(id) if for folder structure.
   *
   * @param id the files' id supplied from MongoDB
   * @param filename files' name supplied from MongoDB
   * @return input byte stream 
   */
  def readFile(id: String, filename:String):InputStream = {     
 
    if (!ipg.conn) {
      ipg.openIRODSConnection()
      Logger.info("irods: Connected. " + ipg.conn)
    }
    
    val filePathHome: String = ipg.userhome + IRODSFile.PATH_SEPARATOR

    val filePath = filePathHome + folderStructure(id) 
        
    Logger.debug("irods: readFile() - filePath: " + filePath + filename)

    try {
	  var file:IRODSFile = irodsFileFactory.instanceIRODSFile(filePath + filename)				  
	  Logger.debug("irods: readFile() - File exists? " + file.exists())				  
 
	  irodsFileFactory.instanceIRODSFileInputStream(file).asInstanceOf[InputStream]
      
    } catch {
    // exceptions return String, need to return null object as an is 
	  case e : IOException => Logger.error("irods: readFile() - Resource failure.." + e.toString()); return null
	  case je: JargonException => Logger.error("irods: readFile() - Error getting an IRODS stream" + je.toString()); return null
    }
   }

  /** Writes file from Medici using IRODSFIleFactory with instance of IRODSFile. Calls 
   *  folderStructure(id) if for folder structure.
   *
   * @param id the files' id generated in IRODSFileSystemDB
   * @param filename files' name supplied by Medici
   * @param InputStream input byte stream supplied by Medici
   */
  def storeFile(id: String, filename:String, is:InputStream) = { 
    var fos: IRODSFileOutputStream = null
        
    if (!ipg.conn) {
      ipg.openIRODSConnection()
      Logger.info("irods: Connected. " + ipg.conn)
    }

    val filePathHome: String = ipg.userhome + IRODSFile.PATH_SEPARATOR
    var filePath: String = filePathHome + folderStructure(id) 
      
    Logger.debug("irods: storeFile() - Input stream bytes: " + is.available())  //Number of bytes that can be read
    
    // fill a buffer Array
    val buffer = Array.ofDim[Byte](10240)
	is.read(buffer)
    is.close()
    
	// append the filename
    if (!filename.isEmpty) {
      filePath = filePath + filename
    } else { filePath = filePath + "unknown.unk"
    }
	Logger.debug("irods: storeFile - filePath: " + filePath)

	try {
	  val file = irodsFileFactory.instanceIRODSFile(filePath)
	  // create folders if they do not exist
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

  /** Deletes file from IRODS using IRODSFIleFactory with instance of IRODSFile. Calls 
   *  folderStructure(id) for folder structure.
   *
   * @param id the files' id from MongoDB
   * @param filename files' name supplied from Medici
   * @return Boolean success
   */
  def deleteFile(id: String, filename:String): Boolean = {
    if (!ipg.conn) {
      ipg.openIRODSConnection()
      Logger.info("irods: Connected. " + ipg.conn)
    }
   try {
      val filePathHome: String = ipg.userhome + IRODSFile.PATH_SEPARATOR 
      val filePath: String = filePathHome + folderStructure(id)
      
      val file: IRODSFile = irodsFileFactory.instanceIRODSFile(filePath + filename)
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
 
   /** creates folder structure n levels deep from two characters of an id string.
   * This follows a DataWolf logic
   * 
   * @param id the files' id from MongoDB
   * @return String path with separators
   */
  def folderStructure(id:String): String = { 
 	var folderPath: String = ""
	  
	var levels:Int = 2
	var i = 0
	while (i < 2*levels) {
	  if (id.length() >= i+2) //break
	  
	  folderPath = folderPath + id.substring(i, i+2) + IRODSFile.PATH_SEPARATOR
      i += 2
    }
 	// creates id folder making the path (plus a filename) unique. The level depth and number of 
 	// id folders control a managable (or allowable) number of file system items 
    if (id.length() > 0) folderPath = folderPath + id + IRODSFile.PATH_SEPARATOR
    
    return folderPath
  }
}