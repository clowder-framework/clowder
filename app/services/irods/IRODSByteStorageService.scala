package services.irods

import java.io._
import java.security.{DigestInputStream, MessageDigest}

import models.UUID
import org.apache.commons.codec.binary.Hex
import org.apache.commons.io.input.CountingInputStream
import org.irods.jargon.core.exception.JargonException
import org.irods.jargon.core.pub.io.IRODSFile
import play.api.{Logger, Play}
import play.api.Play._
import services.ByteStorageService

/**
 * Helper to store data on disk.
 *
 * @author Chris Navarro <cmnavarr@illinois.edu>
 * @author Michal Ondrejcek <ondrejce@illinois.edu>
 * @author Rob Kooper
 */
class IRODSByteStorageService extends ByteStorageService {
  /**
   * Save the bytes to IRODS
   */
  def save(inputStream: InputStream, prefix: String, id: UUID): Option[(String, String, Long)] = {
    current.plugin[IRODSPlugin] match {
      case None => {
        Logger.error("No IRODSPlugin")
        None
      }
      case Some(ipg) => {
        var depth = Play.current.configuration.getInt("irods.depth").getOrElse(2)

        var relativePath = ""
        var idstr = id.stringify
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
        relativePath += java.io.File.separatorChar + id.stringify

        // combine all pieces
        val filePath = makePath(ipg.userhome, prefix, relativePath)

        // make sure the connection is open
        if (!ipg.conn) {
          ipg.openIRODSConnection()
        }

        try {
          // create folder structure
          val file = ipg.getFileFactory().instanceIRODSFile(filePath)
          if (!file.getParentFile.isDirectory && !file.getParentFile.mkdirs()) {
            return None
          }

          // fill a buffer Array
          val md = MessageDigest.getInstance("SHA-512")
          val cis = new CountingInputStream(inputStream)
          val dis = new DigestInputStream(cis, md)
          val fos = ipg.getFileFactory().instanceIRODSFileOutputStream(file)
          val buffer = new Array[Byte](16384)
          var count: Int  = -1
          while({count = dis.read(buffer); count > 0}) {
            fos.write(buffer, 0, count)
          }
          fos.close()

          val sha512 = Hex.encodeHexString(md.digest())
          val length = cis.getByteCount

          // finished
          Some(relativePath, sha512, length)
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
          ipg.closeIRODSConnection()
        }
      }
    }
  }

  /**
   * Get the bytes from IRODS
   */
  def load(id: String, prefix: String): Option[InputStream] = {
    current.plugin[IRODSPlugin] match {
      case None => {
        Logger.error("No IRODSPlugin")
        None
      }
      case Some(ipg) => {
        // combine all pieces
        val filePath = makePath(ipg.userhome, prefix, id)

        // find actual file and delete it
        if (!ipg.conn) {
          ipg.openIRODSConnection()
        }
        try {
          val file = ipg.getFileFactory().instanceIRODSFile(filePath)
          val is = ipg.getFileFactory().instanceIRODSFileInputStream(file)

          // HACK with an intermediary buffer - works, no errors
          // I could not pass the InputStream is directly to Some(InputStream, String, String, Long) because,
          // I think the is.close does not propagate from File > downloads > Enumerator through the chain
          // to the IRODSFileInputStream. Closing the stream here works.
          val buffer = new ByteArrayOutputStream()
          var count: Int = -1
          val data = new Array[Byte](16384)

          while ({count = is.read(data, 0, data.length); count > 0}) {
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
          ipg.closeIRODSConnection()
        }
      }
    }
  }

  /**
   * Delete actual bytes from IRODS
   */
  def delete(id: String, prefix: String): Boolean = {
    current.plugin[IRODSPlugin] match {
      case None => {
        Logger.error("No IRODSPlugin")
        false
      }
      case Some(ipg) => {
        // combine all pieces
        val filePath = makePath(ipg.userhome, prefix, id)

        // find actual file and delete it
        if (!ipg.conn) {
          ipg.openIRODSConnection()
        }
        try {
          val file = ipg.getFileFactory().instanceIRODSFile(filePath)
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
          ipg.closeIRODSConnection()
        }
      }
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
}
