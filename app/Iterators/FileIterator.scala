package Iterators

import java.io.{ByteArrayInputStream, InputStream}
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.{ZipEntry, ZipOutputStream}

import models.ResourceRef
import play.api.libs.json.{JsValue, Json}
import services.{FolderService, MetadataService, FileService}
import util.JSONLD

//this is used for file downloads
//called by the dataset interator
class FileIterator (pathToFile : String, file : models.File,zip : ZipOutputStream, md5Files :scala.collection.mutable.HashMap[String, MessageDigest], files : FileService, folders : FolderService , metadataService : MetadataService) extends Iterator[Option[InputStream]] {

  def getFileInfoAsJson(file : models.File) : JsValue = {
    val rightsHolder = {
      val licenseType = file.licenseData.m_licenseType
      if (licenseType == "license1") {
        file.author.fullName
      } else if (licenseType == "license2") {
        "Creative Commons"
      } else if (licenseType == "license3") {
        "Public Domain Dedication"
      } else {
        "None"
      }

    }
    val licenseInfo = Json.obj("licenseText"->file.licenseData.m_licenseText,"rightsHolder"->rightsHolder)
    Json.obj("id" -> file.id, "filename" -> file.filename, "author" -> file.author.email, "uploadDate" -> file.uploadDate.toString,"contentType"->file.contentType,"description"->file.description,"license"->licenseInfo)
  }
  def addFileInfoToZip(folderName: String, file: models.File, zip: ZipOutputStream): Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(folderName + "/"+file.filename+"_info.json"))
    val fileInfo = getFileInfoAsJson(file)
    val s : String = Json.prettyPrint(fileInfo)
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  def addFileToZip(folderName: String, file: models.File, zip: ZipOutputStream): Option[InputStream] = {
    files.getBytes(file.id) match {
      case Some((inputStream, filename, contentType, contentLength)) => {
        zip.putNextEntry(new ZipEntry(folderName + "/" + filename))
        Some(inputStream)
      }
      case None => None
    }
  }

  def addFileMetadataToZip(folderName: String, file: models.File, zip: ZipOutputStream): Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(folderName + "/"+file.filename+"_metadata.json"))
    val fileMetadata = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.file, file.id)).map(JSONLD.jsonMetadataWithContext(_))
    val s : String = Json.prettyPrint(Json.toJson(fileMetadata))
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  var file_type : Int = 0
  var is : Option[InputStream] = None
  def hasNext() = {
    if ( file_type < 3){
      true
    }
    else
      false
  }
  def next() = {
    file_type match {
      case 0 => {
        file_type +=1
        is  = addFileInfoToZip(pathToFile, file, zip)
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(file.filename+"_info.json",md5)
        Some(new DigestInputStream(is.get,md5))
      }
      case 1 => {
        file_type+=1
        is = addFileMetadataToZip(pathToFile,file,zip)
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(file.filename+"_metadata.json",md5)
        Some(new DigestInputStream(is.get,md5))
      }
      case 2 => {
        file_type+=1
        is = addFileToZip(pathToFile,file,zip)
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(file.filename,md5)
        Some(new DigestInputStream(is.get,md5))
      }
      case _ => None
    }
  }
}

