package Iterators

import java.io.InputStream
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.ZipOutputStream
import scala.collection.mutable.{HashMap, ListBuffer, Map}
import play.api.libs.json.{JsValue, Json}
import play.api.Logger

import models.{ResourceRef, UUID, File, Dataset}
import services._
import util.JSONLD
import Iterators.IteratorUtils


/**
 * This is used to download a dataset.
 *
 * @param pathToFolder - base path into which collection contents will be written inside the output zip file
 * @param dataset - the dataset to be downloaded
 * @param zip - a ZipOutputStream that will receive contents and metadata of the collection
 * @param md5Files - HashMap for MD5 checksums of all data files that go into the zip
 * @param md5Bag - HashMap for MD5 checksums of all BagIt metadata files that go into the zip
 * @param user - if a user is specified, datasets will be limited to their permissions
 * @param bagit - whether or not to include BagIt metadata in the output
 */
class DatasetIterator (pathToFolder: String, dataset: Dataset, zip: ZipOutputStream, md5Files: HashMap[String, MessageDigest])
  extends Iterator[Option[InputStream]] {

  // Guice injection doesn't work in the context we're using this in
  val files = DI.injector.getInstance(classOf[FileService])
  val folders = DI.injector.getInstance(classOf[FolderService])
  val spaces = DI.injector.getInstance(classOf[SpaceService])
  val metadatas = DI.injector.getInstance(classOf[MetadataService])

  // A mapping of file ID to the folder it will be written to in output zip file
  val folderNameMap = Map.empty[UUID, String]

  // A list of all the files within the dataset and its folders
  var inputFilesBuffer = new ListBuffer[File]()

  // Add immediate file children to list
  var foundFiles = files.get(dataset.files).found
  foundFiles.foreach(file => {
    inputFilesBuffer += file
    // Don't create output folder for files unless there's a filename collision
    var foundDuplicate = false
    foundFiles.foreach(compare_file => {
      if (compare_file.filename == file.filename && compare_file.id != file.id)
        foundDuplicate = true
    })
    if (foundDuplicate)
      folderNameMap(file.id) = pathToFolder + "/" + file.filename + "_" + file.id.stringify + "/"
    else
      folderNameMap(file.id) = pathToFolder
  })

  // Add file children inside folders to list
  folders.findByParentDatasetId(dataset.id).foreach(folder => {
    // Walk up the Clowder folder tree until the dataset is found again, to build output folder path in zip
    var breadcrumbs = folder.displayName
    var currentLevel = folder
    while(currentLevel.parentType == "folder") {
      folders.get(currentLevel.parentId) match {
        case Some(fparent) => {
          breadcrumbs = fparent.displayName + "/" + breadcrumbs
          currentLevel = fparent
        }
        case None => Logger.error("Problem fetching parent folder " + currentLevel.parentId.stringify)
      }
    }
    // For each file in the folder, add to the list
    foundFiles = files.get(folder.files).found
    foundFiles.foreach(file => {
      inputFilesBuffer += file
      // Don't create output folder for files unless there's a filename collision
      var foundDuplicate = false
      foundFiles.foreach(compare_file => {
        if (compare_file.filename == file.filename && compare_file.id != file.id) {
          foundDuplicate = true
        }
      })
      if (foundDuplicate)
        folderNameMap(file.id) = pathToFolder + "/" + breadcrumbs + "/" + file.filename+"_"+file.id.stringify + "/"
      else
        folderNameMap(file.id) = pathToFolder + "/" + breadcrumbs + "/"
    })
  })

  val childFiles = inputFilesBuffer.toList
  var currentFile: Option[File] = None
  var currentFileIterator: Option[FileIterator] = None
  var currentFileIdx = 0
  var is: Option[InputStream] = None
  var file_type = "dataset_info"


  private def getDatasetInfoAsJson(dataset: Dataset): JsValue = {
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
          case "license1" => dataset.author.fullName.getOrElse("Limited")
          case "license2" => "Creative Commons"
          case "license3" => "Public Domain Dedication"
          case _ => "None"
        })
      ))
  }

  def hasNext(): Boolean = {
    file_type match {
      case "dataset_info" => true
      case "dataset_metadata" => true
      case "file_iterator" => currentFileIterator match {
        case Some(fileIterator) => {
          if (fileIterator.hasNext()) true
          else if (currentFileIdx < childFiles.size -1) {
            currentFileIdx +=1
            currentFile = Some(childFiles(currentFileIdx))
              currentFileIterator = Some(new FileIterator(folderNameMap(currentFile.get.id),
              childFiles(currentFileIdx), zip, md5Files))
            true
          } else false
        }
        case None => false
      }
      case _ => false
    }
  }

  def next(): Option[DigestInputStream] = {
    file_type match {
      case "dataset_info" => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put("_info.json", md5)
        is = IteratorUtils.addJsonFileToZip(zip, pathToFolder, dataset.name+"_info",
          getDatasetInfoAsJson(dataset))
        file_type = "dataset_metadata"
        Some(new DigestInputStream(is.get, md5))
      }
      case "dataset_metadata" => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put("_metadata.json",md5)
        is = IteratorUtils.addJsonFileToZip(zip, pathToFolder, dataset.name+"_metadata", Json.toJson(
          metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, dataset.id)).map(JSONLD.jsonMetadataWithContext(_))))
        if (childFiles.size > 0) {
          file_type = "file_iterator"
          currentFile = Some(childFiles(currentFileIdx))
          currentFileIterator = Some(new FileIterator(folderNameMap(currentFile.get.id), currentFile.get, zip, md5Files))
        } else file_type = "done"
        Some(new DigestInputStream(is.get, md5))
      }
      case "file_iterator" => {
        currentFileIterator match {
          case Some(fileIterator) => fileIterator.next()
          case None => None
        }
      }
      case _ => None
    }
  }
}