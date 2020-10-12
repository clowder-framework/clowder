package services.irods

import java.io._

import models.UUID
import org.apache.commons.io.input.CountingInputStream
import org.irods.jargon.core.connection.IRODSAccount
import org.irods.jargon.core.exception.JargonException
import org.irods.jargon.core.pub.IRODSFileSystem
import org.irods.jargon.core.pub.io.{IRODSFile, IRODSFileFactory}
import play.api.{Logger, Play}
import services.ByteStorageService

/**
 * Helper to store data on disk.
 *
 */
class IRODSByteStorageService extends ByteStorageService {

  var userhome: String = _

  var account: IRODSAccount = _
  var irodsFileSystem: IRODSFileSystem = _
  var irodsFileFactory: IRODSFileFactory = _
  private var _conn: Boolean = false

  /**
   * Save the bytes to IRODS
   */
  def save(inputStream: InputStream, prefix: String, length: Long): Option[(String, Long)] = {

    var depth = Play.current.configuration.getInt("irods.depth").getOrElse(2)

    var relativePath = ""
    var idstr = UUID.generate().stringify
    // id seems to be same at the start but more variable at the end
    while (depth > 0 && idstr.length > 4) {
      depth -= 1
      if (relativePath == "") {
        relativePath = idstr.takeRight(2)
      } else {
        relativePath += java.io.File.separatorChar + idstr.takeRight(2)
      }
      idstr = idstr.dropRight(2)
    }

    // need to use whole id again, to make sure it is unique
    relativePath += java.io.File.separatorChar + idstr

    // combine all pieces
    val filePath = makePath(userhome, prefix, relativePath)

    // make sure the connection is open
    if (!conn) {
      openIRODSConnection()
    }

    try {
      // create folder structure
      val file = getFileFactory().instanceIRODSFile(filePath)
      if (!file.getParentFile.isDirectory && !file.getParentFile.mkdirs()) {
        return None
      }

      // fill a buffer Array
      val cis = new CountingInputStream(inputStream)
      val fos = getFileFactory().instanceIRODSFileOutputStream(file)
      val buffer = new Array[Byte](16384)
      var count: Int = -1
      while ( {
        count = cis.read(buffer);
        count > 0
      }) {
        fos.write(buffer, 0, count)
      }
      fos.close()

      val length = cis.getByteCount

      // finished
      Some(relativePath, length)
    } catch {
      case e: JargonException => {
        Logger.error("Could not save file " + filePath)
        None
      }
      case e: IOException => {
        Logger.error("Could not save file " + filePath)
        None
      }
    } finally {
      closeIRODSConnection()
    }
  }

  /**
   * Get the bytes from IRODS
   */
  def load(id: String, prefix: String): Option[InputStream] = {
    // combine all pieces
    val filePath = makePath(userhome, prefix, id)

    // find actual file and delete it
    if (!conn) {
      openIRODSConnection()
    }
    try {
      val file = getFileFactory().instanceIRODSFile(filePath)
      val is = getFileFactory().instanceIRODSFileInputStream(file)

      // HACK with an intermediary buffer - works, no errors
      // I could not pass the InputStream is directly to Some(InputStream, String, String, Long) because,
      // I think the is.close does not propagate from File > downloads > Enumerator through the chain
      // to the IRODSFileInputStream. Closing the stream here works.
      val buffer = new ByteArrayOutputStream()
      var count: Int = -1
      val data = new Array[Byte](16384)

      while ( {
        count = is.read(data, 0, data.length); count > 0
      }) {
        buffer.write(data, 0, count)
      }
      is.close()

      Some(new ByteArrayInputStream(buffer.toByteArray))
    } catch {
      case e: JargonException => {
        Logger.error("Could not retrieve file " + filePath)
        None
      }
      case e: IOException => {
        Logger.error("Could not retrieve file " + filePath)
        None
      }
    } finally {
      closeIRODSConnection()
    }
  }

  /**
   * Delete actual bytes from IRODS
   */
  def delete(id: String, prefix: String): Boolean = {
    // combine all pieces
    val filePath = makePath(userhome, prefix, id)

    // find actual file and delete it
    if (!conn) {
      openIRODSConnection()
    }
    try {
      val file = getFileFactory().instanceIRODSFile(filePath)
      if (file.exists()) file.deleteWithForceOption() else true
    } catch {
      case e: JargonException => {
        Logger.error("Could not delete file " + filePath)
        false
      }
      case e: IOException => {
        Logger.error("Could not delete file " + filePath)
        false
      }
    } finally {
      closeIRODSConnection()
    }

  }

  def makePath(root: String, prefix: String, relativePath: String) = {
    // create absolute path
    var filePath = if (root.last != IRODSFile.PATH_SEPARATOR_CHAR) {
      root + IRODSFile.PATH_SEPARATOR
    } else {
      root
    }

    // add the prefix
    if (prefix != "") {
      filePath += prefix + IRODSFile.PATH_SEPARATOR
    }

    // finally create full path
    filePath + relativePath
  }


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
      irodsFileSystem = IRODSFileSystem.instance() // RODSFileSystem shared object, initialized
      Logger.debug("irods: Connecting to " + account.toString())

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
  def getFileFactory(): IRODSFileFactory = {
    return irodsFileFactory
  }

  def conn = _conn

  // setter
  def conn_=(value: Boolean): Unit = _conn = value
}
