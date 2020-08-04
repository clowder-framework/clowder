package Iterators

import java.io.InputStream
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.ZipOutputStream

import scala.collection.mutable.HashMap
import models.{Collection, Dataset, User}
import services._


/**
 * This is used to download the datasets in a collection.
 * It creates an iterator for each dataset in the collection.
 */
class DatasetsInCollectionIterator (pathToFolder: String, collection: Collection, zip: ZipOutputStream, md5Files: HashMap[String, MessageDigest],
                                    user: Option[User]) extends Iterator[Option[InputStream]] {

  // Guice injection doesn't work in the context we're using this in
  val datasets = DI.injector.getInstance(classOf[DatasetService])
  val files = DI.injector.getInstance(classOf[FileService])
  val folders = DI.injector.getInstance(classOf[FolderService])
  val spaces = DI.injector.getInstance(classOf[SpaceService])

  val datasetList = datasets.listCollection(collection.id.stringify, user)
  var currentDatasetIdx = 0
  var currentDataset: Option[Dataset] = None
  var currentDatasetIterator: Option[DatasetIterator] = None
  if (datasetList.size > 0) {
    currentDataset = Some(datasetList(currentDatasetIdx))
    currentDatasetIterator = Some(new DatasetIterator(pathToFolder + "/" + currentDataset.get.name,
      currentDataset.get, zip, md5Files))
  }


  def hasNext(): Boolean = {
    currentDatasetIterator match {
      case Some(datasetIterator) => {
        if (datasetIterator.hasNext()) true
        else {
          if (currentDatasetIdx < datasetList.size -1) {
            currentDatasetIdx +=1
            currentDataset = Some(datasetList(currentDatasetIdx))
            currentDatasetIterator = Some(new DatasetIterator(pathToFolder + "/" + currentDataset.get.name,
              currentDataset.get, zip, md5Files))
            true
          }
          else false
        }
      }
      case None => false
    }
  }

  def next(): Option[DigestInputStream] = {
    currentDatasetIterator match {
      case Some(datasetIterator) => datasetIterator.next()
      case None => None
    }
  }
}
