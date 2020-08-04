package Iterators

import java.io.InputStream
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.ZipOutputStream

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

  var currentDatasetIdx = 0
  var currentDataset = selected(currentDatasetIdx)
  var currentDatasetIterator = new DatasetIterator(pathToFolder+"/"+currentDataset.name, currentDataset, zip, md5Files)
  var bagItIterator : Option[BagItIterator] = None
  var bytesSoFar : Long  = 0L
  var file_type = "dataset_iterator"


  def setBytes(totalBytes : Long) = {
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
      case "dataset_iterator" => {
        if (currentDatasetIterator.hasNext()) true
        else if (selected.length > currentDatasetIdx+1) {
          currentDatasetIdx += 1
          currentDataset = selected(currentDatasetIdx)
          currentDatasetIterator = new DatasetIterator(pathToFolder+"/"+currentDataset.name,
            currentDataset, zip, md5Files)
          true
        } else if (bagit) {
          bagItIterator = Some(new BagItIterator(pathToFolder,
            None, zip, md5Bag, md5Files, bytesSoFar, user))
          file_type = "bagit"
          true
        } else false
      }
      case "bagit" => {
        bagItIterator match {
          case Some(bagIterator) => bagIterator.hasNext()
          case None => false
        }
      }
      case _ => false
    }
  }

  def next(): Option[DigestInputStream] = {
    file_type match {
      case "dataset_iterator" => currentDatasetIterator.next()
      case "bagit" => {
        bagItIterator match {
          case Some(bagIterator) => bagIterator.next()
          case None => None
        }
      }
      case _ => None
    }
  }
}
