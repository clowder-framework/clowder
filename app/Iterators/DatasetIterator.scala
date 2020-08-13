package Iterators

import java.io.InputStream
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.ZipOutputStream
import scala.collection.mutable.{HashMap, ListBuffer, Map => MutaMap}
import play.api.libs.json.Json
import play.api.Logger

import models.{Dataset, File, ResourceRef, UUID, User}
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
class DatasetIterator(pathToFolder: String, dataset: Dataset, zip: ZipOutputStream,
                       md5Files: HashMap[String, MessageDigest], md5Bag: HashMap[String, MessageDigest],
                       user: Option[User], bagit: Boolean) extends Iterator[Option[InputStream]] {

  // Guice injection doesn't work in the context we're using this in
  val files = DI.injector.getInstance(classOf[FileService])
  val folders = DI.injector.getInstance(classOf[FolderService])
  val spaces = DI.injector.getInstance(classOf[SpaceService])
  val metadatas = DI.injector.getInstance(classOf[MetadataService])

  val dataFolder = if (bagit) "data/" else ""
  val (filenameMap, childFiles) = generateUniqueNames(dataset.files, dataset.folders, dataFolder)

  var bagItIterator: Option[BagItIterator] = None
  var currentFile: Option[File] = None
  var currentFileIterator: Option[FileIterator] = None
  var currentFileIdx = 0
  var is: Option[InputStream] = None
  var bytesSoFar = 0L
  var file_type = "dataset_info"


  /**
   * Compute list of all files and folders in dataset. Ensure all
   * files and folder names are unique because Clowder does not
   * enforce uniqueness.
   */
  def generateUniqueNames(fileids: List[UUID], folderids: List[UUID], parent: String): (Map[UUID, String], List[File]) = {
    val filenameMap = MutaMap.empty[UUID, String]
    val inputFilesBuffer = ListBuffer.empty[File]

    val fileobjs = files.get(fileids).found

    // map fileobj to filename, make sure filename is unique
    // potential improvemnt would be to keep a map -> array of ids
    // if array.length == 1, then no duplicate, else fix all duplicate ids
    fileobjs.foreach(f => {
      inputFilesBuffer.append(f)
      if (fileobjs.exists(x => x.id != f.id && x.filename == f.filename)) {
        // create new filename filename_id.ext
        val (filename, ext) = f.filename.lastIndexOf('.') match {
          case(-1) => (f.filename, "")
          case(x) => (f.filename.substring(0, x), f.filename.substring(x))
        }
        filenameMap(f.id) = s"${parent}${filename}_${f.id}${ext}"
      } else {
        filenameMap(f.id) = s"${parent}${f.filename}"
      }
    })

    // get all folder objects
    val folderobjs = folderids.flatMap(x => folders.get(x) match {
      case Some(f) => Some(f)
      case None => {
        Logger.error(s"Could not find folder with id=${x.uuid}")
        None
      }
    })
    folderobjs.foreach(f => {
      val folder = if (folderobjs.exists(x => x.id != f.id && x.displayName == f.displayName)) {
        // this case should not happen since folders are made unique at creation
        s"${parent}${f.displayName}_${f.id.stringify}/"
      } else {
        s"${parent}${f.displayName}/"
      }
      (filenameMap, inputFilesBuffer) = generateUniqueNames(f.files, f.folders, folder)
    })

    (filenameMap.toMap, inputFilesBuffer.toList)
  }

  def setBytes(totalBytes: Long) = {
    bytesSoFar = totalBytes
    bagItIterator match {
      case Some(bagIterator) => bagIterator.setBytes(bytesSoFar)
      case None => {}
    }
  }

  def isBagIt(): Boolean = {
    file_type == "bagit"
  }

  def hasNext(): Boolean = {
    file_type match {
      case "dataset_info" => true
      case "dataset_metadata" => true
      case "file_iterator" => currentFileIterator match {
        case Some(fileIterator) => {
          if (fileIterator.hasNext()) true
          else if (currentFileIdx < childFiles.size-1) {
            currentFileIdx += 1
            currentFile = Some(childFiles(currentFileIdx))
            currentFileIterator = Some(new FileIterator(
              filenameMap(currentFile.get.id), currentFile.get, zip, md5Files))
            true
          } else if (bagit) {
            bagItIterator = Some(new BagItIterator(pathToFolder, zip, dataset.id.stringify, dataset.description,
              md5Bag, md5Files, user))
            file_type = "bagit"
            true
          } else false
        }
        case None => false
      }
      case _ => false
    }
  }

  def next(): Option[DigestInputStream] = {
    var dis: Option[DigestInputStream] = None
    file_type match {
      case "dataset_info" => {
        val filename = s"${dataset.name}_info.json"
        is = IteratorUtils.addJsonFileToZip(zip, pathToFolder, filename,
          IteratorUtils.getDatasetInfoAsJson(dataset))
        dis = IteratorUtils.addMD5Entry(filename, is, md5Files)
        file_type = "dataset_metadata"
      }
      case "dataset_metadata" => {
        val filename = s"${dataset.name}_metadata.json"
        is = IteratorUtils.addJsonFileToZip(zip, pathToFolder, dataset.name+"_metadata", Json.toJson(
          metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, dataset.id)).map(JSONLD.jsonMetadataWithContext(_))))
        dis = IteratorUtils.addMD5Entry(filename, is, md5Files)
        // Only create a file iterator if the dataset has files
        if (childFiles.size > 0) {
          file_type = "file_iterator"
          currentFile = Some(childFiles(currentFileIdx))
          currentFileIterator = Some(new FileIterator(filenameMap(currentFile.get.id),
            currentFile.get, zip, md5Files))
        } else file_type = "done"
      }
      case "file_iterator" => {
        currentFileIterator match {
          case Some(fileIterator) => fileIterator.next()
          case None => {}
        }
      }
      case "bagit" => {
        bagItIterator match {
          case Some(bagIterator) => bagIterator.next()
          case None => None
        }
      }
      case _ => None
    }
    dis
  }
}