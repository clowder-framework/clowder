package services.filesystem

import java.io.{FileInputStream, File, InputStream}
import java.nio.file.{Paths, Files}

import models.UUID
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
   * Save the bytes to disk
   */
  def save(inputStream: InputStream, prefix: String, id: UUID): Option[String] = {
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
        Logger.debug("Saving file to " + filePath)
        Files.copy(inputStream, Paths.get(filePath))
        inputStream.close()

        // store metadata to mongo
        Some(relativePath)
      }
      case None => None
    }
  }

  /**
   * Get the bytes from disk
   */
  def load(relativePath: String, prefix: String): Option[InputStream] = {
    Play.current.configuration.getString("medici2.diskStorage.path") match {
      case Some(root) => {
        // combine all pieces
        val filePath = makePath(root, prefix, relativePath)

        // load the bytes
        Logger.debug("Loading file from " + filePath)
        if (new File(filePath).exists()) {
          Some(new FileInputStream(filePath))
        } else {
          None
        }
      }
      case None => None
    }
  }

  /**
   * Delete actualy bytes from disk
   */
  def delete(relativePath: String, prefix: String): Boolean = {
    Play.current.configuration.getString("medici2.diskStorage.path") match {
      case Some(root) => {
        // combine all pieces
        val filePath = makePath(root, prefix, relativePath)

        // delete the bytes
        Logger.info("Removing file " + filePath)
        if (new File(filePath).exists()) new File(filePath).delete() else true
      }
      case None => false
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
