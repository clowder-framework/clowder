package Iterators

import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipOutputStream

import models.{Dataset, User}
import services._

import scala.collection.mutable.ListBuffer

//this is used to download the datasets in a collection
//it creates an iterator for each dataset in the collection
class DatasetsInCollectionIterator(pathToFolder : String, collection : models.Collection, zip : ZipOutputStream, md5Files : scala.collection.mutable.HashMap[String, MessageDigest], user : Option[User],
                                  datasets : DatasetService, files : FileService, folders : FolderService, metadataService : MetadataService,
                                   spaces : SpaceService) extends Iterator[Option[InputStream]] {


  def getDatasetsInCollection(collection : models.Collection,user : User) : List[Dataset] = {
    var datasetsInCollection : ListBuffer[Dataset] = ListBuffer.empty[Dataset]
    var datasetsInCollectionList = datasets.listCollection(collection.id.stringify,Some(user))
    datasetsInCollectionList
  }



  val datasetsInCollection = getDatasetsInCollection(collection, user.get)

  var datasetCount = 0
  val numDatasets = datasetsInCollection.size

  var currentDataset : Option[Dataset] = if (numDatasets > 0){
    Some(datasetsInCollection(datasetCount))
  } else {
    None
  }

  var currentDatasetIterator : Option[DatasetIterator]  = if (numDatasets > 0){

    Some(new DatasetIterator(pathToFolder+"/"+currentDataset.get.name,currentDataset.get, zip, md5Files,
    folders, files,metadataService,datasets,spaces))
  } else {
    None
  }


  def hasNext() = {

    currentDatasetIterator match {
      case Some(datasetIterator) => {
        if (datasetIterator.hasNext()){
          true
        } else {
          if (datasetCount < numDatasets -1){
            datasetCount +=1
            currentDataset = Some(datasetsInCollection(datasetCount))
            currentDataset match {
              case Some(cd) => {
                currentDatasetIterator = Some(new DatasetIterator(pathToFolder+"/"+cd.name,cd, zip, md5Files,
                  folders, files,metadataService,datasets,spaces))
                true
              }
              case None => false
            }
          } else
            false
        }
      }
      case None => false
    }
  }

  def next() = {
    currentDatasetIterator match {
      case Some(datasetIterator) => datasetIterator.next()
      case None => None
    }
  }
}
