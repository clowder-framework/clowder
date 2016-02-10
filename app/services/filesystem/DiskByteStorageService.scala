package services.filesystem

import java.io.{FileInputStream, File, InputStream}
import java.nio.file.{Paths, Files}
import java.security.{MessageDigest, DigestInputStream}

import models.UUID
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.input.CountingInputStream
import play.Logger
import play.api.Play
import services.ByteStorageService

/**
 * Helper to store data on disk.
 *
 * @author Rob Kooper
 */
class DiskByteStorageService extends ByteStorageService {
  /**
   * Save the bytes to disk, returns (path, sha512, length)
   */
  def save(inputStream: InputStream, prefix: String, id: UUID): Option[(String, String, Long)] = {
    Play.current.configuration.getString("medici2.diskStorage.path") match {
      case Some(root) => {
        var depth = Play.current.configuration.getInt("medici2.diskStorage.depth").getOrElse(3)

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
        val filePath = makePath(root, prefix, relativePath)

        // create subfolders
        val parent = new File(filePath).getParentFile
        if (!parent.exists() && !parent.mkdirs()) {
          Logger.error("Could not create folder on disk " + new File(filePath).getParent)
          return None
        }

        // save actual bytes
        val md = MessageDigest.getInstance("SHA-512")
        val cis = new CountingInputStream(inputStream)
        val dis = new DigestInputStream(cis, md)
        Logger.debug("Saving file to " + filePath)
        Files.copy(dis, Paths.get(filePath))
        dis.close()

        val sha512 = Hex.encodeHexString(md.digest())
        val length = cis.getByteCount

        // store metadata to mongo
        Some((filePath, sha512, length))
      }
      case None => None
    }
  }

  /**
    * Save existing path to bytes on disk, returns (path, sha512, length)
    */
  def saveInPlace(filePath: String, inputStream: InputStream): Option[(String, String, Long)] = {
    Logger.debug("saveInPlace - DBSS")
    // save actual bytes
    val md = MessageDigest.getInstance("SHA-512")
    val cis = new CountingInputStream(inputStream)
    val dis = new DigestInputStream(cis, md)
    Logger.debug("Saving existing file at " + filePath)
    dis.close()

    val sha512 = Hex.encodeHexString(md.digest())
    val length = cis.getByteCount

    // store metadata to mongo
    Some((filePath, sha512, length))
  }

  /**
   * Get the bytes from disk
   */
  def load(path: String, ignored: String): Option[InputStream] = {
    // load the bytes
    Logger.debug("Loading file from " + path)
    if (new File(path).exists()) {
      Some(new FileInputStream(path))
    } else {
      None
    }
  }

  /**
   * Delete actualy bytes from disk
   */
  def delete(path: String, prefix: String): Boolean = {
    Play.current.configuration.getString("medici2.diskStorage.path") match {
      case Some(root) => {
        if (path.startsWith(makePath(root, prefix, ""))) {
          // delete the bytes
          Logger.debug("Removing file " + path)
          var file = new File(path)
          val result = if (file.exists())
            file.delete()
          else
            true

          // cleanup folder
          file = file.getParentFile
          while (file.list().isEmpty && file.getName != prefix) {
            file.delete()
            file = file.getParentFile
          }
          result
        } else {
          Logger.warn(s"Not removing file ${path}, not inside ${root}")
          true
        }
      }
      case None => true
    }
  }

  def makePath(root: String, prefix: String, relativePath: String) = {
    // create absolute path
    var filePath = if (root.last != java.io.File.separatorChar) {
      root + java.io.File.separatorChar
    } else {
      root
    }

    // add the prefix
    if (prefix != "") {
      filePath += prefix + java.io.File.separatorChar
    }

    // finally create full path
    filePath + relativePath
  }
}
