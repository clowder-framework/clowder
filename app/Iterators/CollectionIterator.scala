package Iterators

import java.io.{ByteArrayInputStream, InputStream}
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.{ZipEntry, ZipOutputStream}

import models.{Collection, User}
import play.api.libs.json.Json._
import play.api.libs.json.{JsValue, Json}
import services._

import scala.collection.mutable.ListBuffer

//this is to download collections
//that are not at the root level
class CollectionIterator(pathToFolder : String, parent_collection : models.Collection,zip : ZipOutputStream, md5Files : scala.collection.mutable.HashMap[String, MessageDigest], user : Option[User],
                         collections: CollectionService, datasets : DatasetService, files : FileService, folders : FolderService, metadataService : MetadataService,
                         spaces : SpaceService) extends Iterator[Option[InputStream]] {


  def getNextGenerationCollections(currentCollections : List[Collection]) : List[Collection] = {
    var nextGenerationCollections : ListBuffer[Collection] = ListBuffer.empty[Collection]
    for (currentCollection <- currentCollections){
      collections.get(currentCollection.child_collection_ids).found.foreach(child_col => {
        nextGenerationCollections += child_col
      })
    }
    nextGenerationCollections.toList
  }

  val datasetIterator = new DatasetsInCollectionIterator(pathToFolder,parent_collection,zip,md5Files,user,
    datasets,files, folders, metadataService,spaces)

  var currentCollectionIterator : Option[CollectionIterator] = None

  //make list
  var child_collections : List[Collection] = getNextGenerationCollections(List(parent_collection))


  var childCollectionCount = 0
  var numChildCollections = child_collections.size

  var file_type = 0

  // TODO: Repeat from api/Collections
  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description,
      "created" -> collection.created.toString,"author"-> collection.author.email.toString, "root_flag" -> collections.hasRoot(collection).toString,
      "child_collection_ids"-> collection.child_collection_ids.mkString(","), "parent_collection_ids" -> collection.parent_collection_ids.mkString(","),
      "childCollectionsCount" -> collection.childCollectionsCount.toString, "datasetCount"-> collection.datasetCount.toString, "spaces" -> collection.spaces.mkString(",")))
  }

  def getCollectionInfoAsJson(collection : models.Collection) : JsValue = {
    val author = collection.author.fullName
    Json.obj("description"->collection.description,"created"->collection.created.toString)
  }

  private def addCollectionInfoToZip(folderName: String, collection: models.Collection, zip: ZipOutputStream): Option[InputStream] = {
    val path = folderName + "/"+collection.name+"_info.json"
    zip.putNextEntry(new ZipEntry(folderName + "/"+collection.name+"_info.json"))
    val infoListMap = Json.prettyPrint(jsonCollection(collection))
    Some(new ByteArrayInputStream(infoListMap.getBytes("UTF-8")))
  }

  private def addCollectionMetadataToZip(folderName : String , collection : models.Collection, zip : ZipOutputStream) : Option[InputStream] = {
    val path = folderName+"/"+collection.name+"_metadata.json"
    zip.putNextEntry(new ZipEntry(folderName+"/"+collection.name+"_metadata.json"))
    val collectionMetadata = getCollectionInfoAsJson(collection)
    val metadataMap = Json.prettyPrint(collectionMetadata)
    Some(new ByteArrayInputStream(metadataMap.getBytes("UTF-8")))
  }

  def hasNext() = {
    if (file_type < 2){
      true
    }
    else if (file_type ==2){
      if (datasetIterator.hasNext()){
        true
      } else if (numChildCollections > 0){

        currentCollectionIterator = Some(new CollectionIterator(pathToFolder+"/"+child_collections(childCollectionCount).name, child_collections(childCollectionCount),zip,md5Files,user,collections,datasets,files,
          folders,metadataService,spaces))
        file_type +=1
        true
      } else {
        false
      }
    } else if (file_type == 3) {
      currentCollectionIterator match {
        case Some(collectionIterator) => {
          if (collectionIterator.hasNext()){
            true
          } else if (childCollectionCount < numChildCollections -2){
            childCollectionCount+=1
            currentCollectionIterator = Some(new CollectionIterator(pathToFolder+"/"+child_collections(childCollectionCount).name, child_collections(childCollectionCount),zip,md5Files,user,
              collections,datasets,files,
              folders,metadataService,spaces))
            true
          } else {
            file_type+=1
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
      //collection info
      case 0 => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(pathToFolder+"_info.json",md5)
        val is = addCollectionInfoToZip(pathToFolder, parent_collection,zip)
        file_type+=1
        Some(new DigestInputStream(is.get, md5))
      }
      //collection metadata
      case 1 => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(pathToFolder+"_metadata.json",md5)
        val is = addCollectionMetadataToZip(pathToFolder, parent_collection,zip)
        file_type+=1
        Some(new DigestInputStream(is.get, md5))
      }
      //datasets in this collection
      case 2 => {
        datasetIterator.next()
      }
      case 3 => {
        currentCollectionIterator match {
          case Some(collectionIterator) => {
            collectionIterator.next()
          }
          case None => None
        }
      }
      case _ => {
        None
      }
    }
  }
}
