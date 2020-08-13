package Iterators

import java.io.InputStream
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.ZipOutputStream
import scala.collection.mutable.HashMap

import models.{Dataset, User}
import services._


/**
  * this class is used to download user selections. it has a bagit iterator as well.
  */
class SelectedIterator(pathToFolder: String, selected: List[Dataset], zip: ZipOutputStream,
                       md5Files: HashMap[String, MessageDigest], md5Bag: HashMap[String, MessageDigest],
                       user: Option[User], bagit: Boolean) extends Iterator[Option[InputStream]] {

  var currentDatasetIdx = 0
  var currentDataset = selected(currentDatasetIdx)
  var currentDatasetIterator = new DatasetIterator(pathToFolder+"/"+currentDataset.name,
    currentDataset, zip, md5Files, md5Bag, user, false)
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
            currentDataset, zip, md5Files, md5Bag, user, false)
          true
        } else if (bagit) {
          val senderId = user match {
            case Some(u) => u.id.stringify
            case None => "Unknown User"
          }
          bagItIterator = Some(new BagItIterator(pathToFolder, zip, senderId, "User Dataset Selections",
            md5Bag, md5Files, user))
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
