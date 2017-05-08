package Iterators

import java.io.{ByteArrayInputStream, InputStream}
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.{ZipEntry, ZipOutputStream}

import models._
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import services._
import util.JSONLD

import scala.collection.mutable.ListBuffer

//this iterator is used for downloading a dataset
//it has a file iterator
class DatasetIterator(pathToFolder : String, dataset : models.Dataset, zip: ZipOutputStream, md5Files :scala.collection.mutable.HashMap[String, MessageDigest],
                      folders : FolderService, files: FileService, metadataService : MetadataService, datasets: DatasetService, spaces : SpaceService) extends Iterator[Option[InputStream]] {

  //get files in the dataset
  def getDatasetInfoAsJson(dataset : Dataset) : JsValue = {
    val rightsHolder = {
      val licenseType = dataset.licenseData.m_licenseType
      if (licenseType == "license1") {
        dataset.author.fullName
      } else if (licenseType == "license2") {
        "Creative Commons"
      } else if (licenseType == "license3") {
        "Public Domain Dedication"
      } else {
        "None"
      }
    }

    val spaceNames = for (
      spaceId <- dataset.spaces;
      space <- spaces.get(spaceId)
    ) yield {
      space.name
    }

    val licenseInfo = Json.obj("licenseText"->dataset.licenseData.m_licenseText,"rightsHolder"->rightsHolder)
    Json.obj("id"->dataset.id,"name"->dataset.name,"author"->dataset.author.email,"description"->dataset.description, "spaces"->spaceNames.mkString(","),"lastModified"->dataset.lastModifiedDate.toString,"license"->licenseInfo)
  }

  def addDatasetInfoToZip(folderName: String, dataset: models.Dataset, zip: ZipOutputStream): Option[InputStream] = {
    val path = folderName + "/"+dataset.name+"_info.json"
    zip.putNextEntry(new ZipEntry(folderName + "/"+dataset.name+"_info.json"))
    val infoListMap = Json.prettyPrint(getDatasetInfoAsJson(dataset))
    Some(new ByteArrayInputStream(infoListMap.getBytes("UTF-8")))
  }

  def addDatasetMetadataToZip(folderName: String, dataset : models.Dataset, zip: ZipOutputStream): Option[InputStream] = {
    val path = folderName + "/"+dataset.name+"_dataset_metadata.json"
    zip.putNextEntry(new ZipEntry(folderName + "/"+dataset.name+"_metadata.json"))
    val datasetMetadata = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, dataset.id))
      .map(JSONLD.jsonMetadataWithContext(_))
    val s : String = Json.prettyPrint(Json.toJson(datasetMetadata))
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  val folderNameMap = scala.collection.mutable.Map.empty[UUID, String]
  var inputFilesBuffer = new ListBuffer[File]()
  dataset.files.foreach(f=>files.get(f) match {
    case Some(file) => {
      inputFilesBuffer += file

      // Don't create folder for files unless there's a filename collision
      var foundDuplicate = false
      dataset.files.foreach(compare_f=>files.get(compare_f) match {
        case Some(compare_file) => {
          if (compare_file.filename == file.filename && compare_file.id != file.id) {
            foundDuplicate = true
          }
        }
        case None => Logger.error(s"No file with id $f")
      })
      if (foundDuplicate)
        folderNameMap(file.id) = pathToFolder + "/" + file.filename + "_" + file.id.stringify + "/"
      else
        folderNameMap(file.id) = pathToFolder
    }
    case None => Logger.error(s"No file with id $f")
  })

  folders.findByParentDatasetId(dataset.id).foreach{
    folder => folder.files.foreach(f=> files.get(f) match {
      case Some(file) => {
        inputFilesBuffer += file
        var name = folder.displayName
        var f1: Folder = folder
        while(f1.parentType == "folder") {
          folders.get(f1.parentId) match {
            case Some(fparent) => {
              name = fparent.displayName + "/"+ name
              f1 = fparent
            }
            case None =>
          }
        }

        // Don't create folder for files unless there's a filename collision
        var foundDuplicate = false
        folder.files.foreach(compare_f=> files.get(compare_f) match {
          case Some(compare_file) => {
            if (compare_file.filename == file.filename && compare_file.id != file.id) {
              foundDuplicate = true
            }
          }
          case None => Logger.error(s"No file with id $f")
        })
        if (foundDuplicate)
          folderNameMap(file.id) = pathToFolder + "/" + name + "/" + file.filename + "_" + file.id.stringify + "/"
        else
          folderNameMap(file.id) = pathToFolder + "/" + name + "/"
      }
      case None => Logger.error(s"No file with id $f")
    })
  }
  val inputFiles = inputFilesBuffer.toList

  val numFiles = inputFiles.size

  var fileCounter = 0

  var currentFileIterator : Option[FileIterator] = None

  var is : Option[InputStream] = None
  var file_type : Int = 0

  def hasNext() = {
    if (file_type < 2){
      true
    } else if (file_type == 2){
      currentFileIterator match {
        case Some(fileIterator) => {
          if (fileIterator.hasNext()){
            true
          } else if (fileCounter < numFiles -1){
            fileCounter +=1
            currentFileIterator = Some(new FileIterator(folderNameMap(inputFiles(fileCounter).id),inputFiles(fileCounter),zip,md5Files,files,folders,metadataService))
            true
          } else {
            false
          }
        }
        case None => false
      }
    } else {
      false
    }

  }

  def next() = {
    file_type match {
      case 0 => {
        is = addDatasetInfoToZip(pathToFolder,dataset,zip)
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put("_info.json",md5)
        file_type+=1
        Some(new DigestInputStream(is.get, md5))
      }
      case 1 => {
        is = addDatasetMetadataToZip(pathToFolder,dataset,zip)
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put("_metadata.json",md5)
        if (numFiles > 0){
          file_type+=1
          currentFileIterator = Some(new FileIterator(folderNameMap(inputFiles(fileCounter).id),inputFiles(fileCounter),zip,md5Files,files,folders,metadataService))
        } else {
          file_type+=2
        }

        Some(new DigestInputStream(is.get, md5))
      }
      case 2 => {
        currentFileIterator match {
          case Some(fileIterator) => {
            fileIterator.next()
          }
          case None => None
        }
      }
    }
  }
}