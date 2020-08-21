package Iterators

import java.io.{ByteArrayInputStream, InputStream}
import java.security.{DigestInputStream, MessageDigest}
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.collection.mutable.HashMap
import play.api.libs.json.{JsValue, Json}
import models.{Collection, Dataset, File, User}
import org.apache.commons.codec.binary.Hex
import services.{CollectionService, DI, SpaceService}


object IteratorUtils {
  val collections = DI.injector.getInstance(classOf[CollectionService])
  val spaces = DI.injector.getInstance(classOf[SpaceService])

  /**
   * Add a .json file containing information about resource.
   * The file in zip will be at: zipPath/outFile.json with contents jsonData.
   *
   * @param zipPath  - where in zip file the info file will be written (i.e. pathToFolder)
   * @param outFile  - name of output filename
   * @param jsonData - contents to include
   */
  def addJsonFileToZip(zip: ZipOutputStream, zipPath: String, outFile: String, jsonData: JsValue): Option[InputStream] = {
    val path = if (outFile.endsWith(".json"))
      zipPath + "/" + outFile
    else
      zipPath + "/" + outFile + ".json"

    zip.putNextEntry(new ZipEntry(path))
    Some(new ByteArrayInputStream(
      Json.prettyPrint(jsonData).getBytes("UTF-8")
    ))
  }

  /**
   * Add a .txt file containing information about resource.
   * The file in zip will be at: zipPath/outFile.txt with contents textData.
   *
   * @param zipPath  - where in zip file the info file will be written (i.e. pathToFolder)
   * @param outFile  - name of output filename
   * @param textData - contents to include
   */
  def addTextFileToZip(zip: ZipOutputStream, zipPath: String, outFile: String, textData: String): Option[InputStream] = {
    val path = if (outFile.endsWith(".txt"))
      zipPath + "/" + outFile
    else
      zipPath + "/" + outFile + ".txt"

    zip.putNextEntry(new ZipEntry(path))
    Some(new ByteArrayInputStream(
      textData.getBytes("UTF-8")
    ))
  }

  /**
   * Add an entry to the MD5 hash map for the given filename & input stream.
   */
  def addMD5Entry(filename: String, is: Option[InputStream], md5HashMap: HashMap[String, MessageDigest]): Option[DigestInputStream] = {
    val md5 = MessageDigest.getInstance("MD5")
    md5HashMap.put(filename, md5)
    Some(new DigestInputStream(is.get, md5))
  }

  def getFileInfoAsJson(file: File): JsValue = {
    Json.obj(
      "id" -> file.id,
      "filename" -> file.filename,
      "author" -> file.author.email,
      "uploadDate" -> file.uploadDate.toString,
      "contentType" -> file.contentType,
      "description" -> file.description,
      "license" -> Json.obj(
        "licenseText" -> file.licenseData.m_licenseText,
        "rightsHolder" -> (file.licenseData.m_licenseType match {
          case "license1" => file.author.fullName.getOrElse[String]("Limited")
          case "license2" => "Creative Commons"
          case "license3" => "Public Domain Dedication"
          case _ => "None"
        })
      ))
  }

  def getDatasetInfoAsJson(dataset: Dataset): JsValue = {
    val spaceNames = spaces.get(dataset.spaces).found.map(s => s.name)

    Json.obj(
      "id" -> dataset.id,
      "name" -> dataset.name,
      "author" -> dataset.author.email,
      "description" -> dataset.description,
      "spaces" -> spaceNames.toString,
      "lastModified" -> dataset.lastModifiedDate.toString,
      "license" -> Json.obj(
        "licenseText" -> dataset.licenseData.m_licenseText,
        "rightsHolder" -> (dataset.licenseData.m_licenseType match {
          case "license1" => dataset.author.fullName.getOrElse[String]("Limited")
          case "license2" => "Creative Commons"
          case "license3" => "Public Domain Dedication"
          case _ => "None"
        })
      )
    )
  }

  def getCollectionInfoAsJson(collection: Collection): JsValue = {
    Json.obj(
      "id" -> collection.id.toString,
      "name" -> collection.name,
      "description" -> collection.description,
      "created" -> collection.created.toString,
      "author"-> collection.author.email.toString,
      "root_flag" -> collections.hasRoot(collection).toString,
      "child_collection_ids"-> collection.child_collection_ids.toString,
      "parent_collection_ids" -> collection.parent_collection_ids.toString,
      "childCollectionsCount" -> collection.childCollectionsCount.toString,
      "datasetCount"-> collection.datasetCount.toString,
      "spaces" -> collection.spaces.toString
    )
  }

  def getBagItTxtHeader(totalbytes: Long, totalFiles: Long,
                         senderId: String, description: String, user: Option[User]): String = {
    var s = ""
    s += "Bag-Software-Agent: clowder.ncsa.illinois.edu\n"
    s += "Bagging-Date: " + (new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")).format(Calendar.getInstance.getTime) + "\n"
    s += "Bag-Size: " + _root_.util.Formatters.humanReadableByteCount(totalbytes) + "\n"
    s += "Payload-Oxum: " + totalbytes + "." + totalFiles + "\n"
    s += "Internal-Sender-Identifier: " + senderId + "\n"
    s += "Internal-Sender-Description: " + description + "\n"
    if (user.isDefined) {
      s += "Contact-Name: " + user.get.fullName + "\n"
      s += "Contact-Email: " + user.get.email.getOrElse("") + "\n"
    }
    s
  }

  def getBagInfoTxt(): String = {
    var s = ""
    s += "BagIt-Version: 0.97\n"
    s += "Tag-File-Character-Encoding: UTF-8\n"
    s
  }

  def generateBagMD5String(md5Map: HashMap[String, MessageDigest]): String = {
    var s = ""
    md5Map.foreach{
      case (entry, md5) => s += s"${Hex.encodeHexString(md5.digest)} $entry\n"
    }
    s
  }
}
