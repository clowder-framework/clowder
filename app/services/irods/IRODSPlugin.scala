package services.irods

import play.api.{ Play, Plugin, Logger, Application }
import org.irods.jargon.core.connection.IRODSAccount
import org.irods.jargon.core.pub.IRODSFileSystem
import org.irods.jargon.core.pub.io.IRODSFileFactory
import org.irods.jargon.core.exception.JargonException

/**
 * A concrete storage based on the iRODS file storage system. There are 7
 * required parameters to establish a connection to iRODS including a
 * host, port, username, password, user home, zone, and default storage resource
 * name. 
 * 
 * Setup iRODS connection using Jargon.
 *
 * @date 2014-08-18
 * 
 */
class IRODSPlugin(app: Application) extends Plugin {

  var userhome: String = _
  
  var account: IRODSAccount = _
  var irodsFileSystem: IRODSFileSystem = _
  var irodsFileFactory: IRODSFileFactory = _
  private var _conn: Boolean = false 

  override def onStart() {
    Logger.info("Starting iRODS Plugin.")
    openIRODSConnection()
    Logger.info("irods: Connected. " + conn)
 }
	
  override def onStop() {
	// close connection
	closeIRODSConnection()
	Logger.info("iRODSPlugin has stopped.")
  }

  //Is the plugin enabled? 
  override def enabled = true
  
  
  def openIRODSConnection() = {
    lazy val configuration = Play.current.configuration
	// you can now access the application.conf settings
		
	// seven required fields (similar to .irodsEnv file used in icommands client)
	val host = configuration.getString("irods.host").getOrElse("")
	val port = configuration.getInt("irods.port").getOrElse(0)
	val username = configuration.getString("irods.username").getOrElse("")
	val password = configuration.getString("irods.password").getOrElse("")
	val zone = configuration.getString("irods.zone").getOrElse("")
	val defaultStorageResource = configuration.getString("irods.defaultStorageResource").getOrElse("")
	userhome = configuration.getString("irods.userhome").getOrElse("")
		
	val usercurrent = configuration.getString("irods.usercurrent").getOrElse("")

  	try {
  	  account = IRODSAccount.instance(host, port, username, password, userhome, zone, defaultStorageResource)
      irodsFileSystem = IRODSFileSystem.instance()	// RODSFileSystem shared object, initialized
	  Logger.info("irods: Connecting to " + account.toString())

	  // the actual connection, For a given account creates an IRODSFileFactory that can return iRODS file objects for this particular connection.
      irodsFileFactory = irodsFileSystem.getIRODSFileFactory(account)
      
	  _conn = true
	} catch {
	  case je: org.irods.jargon.core.exception.JargonException => Logger.error("irods: Error connecting to iRODS server. " + je.toString); _conn = false
	  case t: Throwable => Logger.error("irods: Unknown error connecting to iRODS server: " + t.toString); _conn = false
	}
  }

  def closeIRODSConnection() = {
	Logger.info("irods: Closing connection.")
	irodsFileSystem.closeAndEatExceptions()
	_conn = false
  }

  // Close the session that is connected to the particular iRODS server with the given account.
  def closeIRODSAccountConnection() = {
	Logger.info("irods: Closing account connection.")
	irodsFileSystem.closeAndEatExceptions(account)
	_conn = false
  }
 
  //getters
  def getFileFactory(): IRODSFileFactory = { return irodsFileFactory }
  def conn = _conn
  // setter
  def conn_= (value:Boolean):Unit = _conn = value 
}