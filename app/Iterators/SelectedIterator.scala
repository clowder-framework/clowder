package Iterators

import java.io.{InputStream}
import java.security.{MessageDigest}
import java.util.zip.{ZipOutputStream}
import play.api.Logger

import models.{Dataset, User}
import services._


/**
  * this class is used to download user selections. it has a bagit iterator as well.
  */
class SelectedIterator(pathToFolder : String, selected : List[Dataset], zip : ZipOutputStream,
                       md5Files : scala.collection.mutable.HashMap[String, MessageDigest],
                       md5Bag : scala.collection.mutable.HashMap[String, MessageDigest],
                       user : Option[User], totalBytes : Long, bagit : Boolean,
                       datasets : DatasetService, files : FileService, folders : FolderService, metadataService : MetadataService,
                       spaces : SpaceService) extends Iterator[Option[InputStream]] {

  var datasetCount = 0
  var currDs = selected(datasetCount)
  var datasetIterator = new DatasetIterator(pathToFolder+"/"+currDs.name, currDs, zip, md5Files, folders, files,
    metadataService,datasets,spaces)
  var file_type = 0


  var bagItIterator : Option[BagItIterator] = None

  var bytesSoFar : Long  = 0L


  def setBytes(totalBytes : Long) = {
    bytesSoFar = totalBytes
    bagItIterator match {
      case Some(bagIterator) => bagIterator.setBytes(bytesSoFar)
      case None =>
    }
  }

  def isBagIt() = {
    if (file_type == 1){
      true
    } else {
      false
    }
  }

  def hasNext() = {
    if (file_type ==0){
      if (datasetIterator.hasNext()){
        true
      } else if (selected.length > datasetCount+1){
        datasetCount += 1
        currDs = selected(datasetCount)
        datasetIterator = new DatasetIterator(pathToFolder+"/"+currDs.name,currDs,zip,md5Files,folders,files,
          metadataService,datasets,spaces)
        true
      } else if (bagit) {
        bagItIterator = Some(new BagItIterator(pathToFolder,None ,zip,md5Bag,md5Files,bytesSoFar ,user))
        file_type = 1
        true
      } else {
        false
      }
    } else if (file_type == 1 && bagit){
      bagItIterator match {
        case Some(bagIterator) => {
          if (bagIterator.hasNext()){
            true
          } else {
            file_type +=1
            false
          }
        }
        case None => {
          false
        }
      }
    } else {
      false
    }
  }

  def next() = {
    file_type match {
      //datasets in this selection
      case 0 => {
        datasetIterator.next()
      }
      //bag it
      case 1 => {
        bagItIterator match {
          case Some(bagIterator) => bagIterator.next()
          case None => None
        }
      }
      //the end
      case _ => {
        None
      }
    }

  }
}
