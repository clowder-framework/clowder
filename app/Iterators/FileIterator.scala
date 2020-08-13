package Iterators

import java.io.{ByteArrayInputStream, InputStream}
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.collection.mutable.HashMap
import play.api.libs.json.{JsValue, Json}
import models.{ResourceRef, File}
import services.{DI, FileService, FolderService, MetadataService}
import util.JSONLD


/**
 * This is used to download a file.
 *
 * @param pathToFile - base path into which collection contents will be written inside the output zip file
 * @param file - the collection to be downloaded
 * @param zip - a ZipOutputStream that will receive contents and metadata of the collection
 * @param md5Files - HashMap for MD5 checksums of all data files that go into the zip
 * @param md5Bag - HashMap for MD5 checksums of all BagIt metadata files that go into the zip
 * @param user - if a user is specified, datasets will be limited to their permissions
 * @param bagit - whether or not to include BagIt metadata in the output
 */
class FileIterator (pathToFile: String, file: File, zip: ZipOutputStream, md5Files: HashMap[String, MessageDigest])
  extends Iterator[Option[InputStream]] {

  // Guice injection doesn't work in the context we're using this in
  val files = DI.injector.getInstance(classOf[FileService])
  val folders = DI.injector.getInstance(classOf[FolderService])
  val metadatas = DI.injector.getInstance(classOf[MetadataService])

  var is : Option[InputStream] = None
  var file_type = "file_info"


  def hasNext(): Boolean = {
    file_type match {
      case "file_info" => true
      case "file_metadata" => true
      case "file_bytes" => true
      case _ => false
    }
  }

  def next(): Option[DigestInputStream] = {
    file_type match {
      case "file_info" => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(file.filename+"_info.json", md5)
        is = IteratorUtils.addJsonFileToZip(zip, pathToFile, file.filename+"_info",
          IteratorUtils.getFileInfoAsJson(file))
        file_type = "file_metadata"
        Some(new DigestInputStream(is.get, md5))
      }
      case "file_metadata" => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(file.filename+"_metadata.json", md5)
        is = IteratorUtils.addJsonFileToZip(zip, pathToFile, file.filename+"_metadata", Json.toJson(
          metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.file, file.id)).map(JSONLD.jsonMetadataWithContext(_))))
        file_type = "file_bytes"
        Some(new DigestInputStream(is.get, md5))
      }
      case "file_bytes" => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(file.filename, md5)
        is = files.getBytes(file.id) match {
          case Some((inputStream, filename, contentType, contentLength)) => {
            zip.putNextEntry(new ZipEntry(pathToFile + "/" + filename))
            Some(inputStream)
          }
          case None => None
        }
        file_type = "done"
        Some(new DigestInputStream(is.get, md5))
      }
      case _ => None
    }
  }
}

