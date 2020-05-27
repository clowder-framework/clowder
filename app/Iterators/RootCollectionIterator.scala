package Iterators

import java.io.{ByteArrayInputStream, InputStream}
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.{ZipEntry, ZipOutputStream}

import models.{ResourceRef, Collection, User}
import play.api.libs.json.Json._
import play.api.libs.json.{JsValue, Json}
import services._
import util.JSONLD

import scala.collection.mutable.ListBuffer

//this class is used to download collection. It is the root level iterator called on the
//collection to download. unlike the other collection iterator, it has a bagit iterator
class RootCollectionIterator(pathToFolder : String, root_collection : models.Collection,zip : ZipOutputStream,
                             md5Files : scala.collection.mutable.HashMap[String, MessageDigest],
                             md5Bag : scala.collection.mutable.HashMap[String, MessageDigest],
                             user : Option[User],totalBytes : Long,bagit : Boolean,
                             collections: CollectionService, datasets : DatasetService, files : FileService, folders : FolderService, metadataService : MetadataService,
                             spaces : SpaceService) extends Iterator[Option[InputStream]] {

  val datasetIterator = new DatasetsInCollectionIterator(root_collection.name,root_collection,zip,md5Files,user,
    datasets,files,folders,metadataService,spaces)

  var currentCollectionIterator : Option[CollectionIterator] = None

  var bagItIterator : Option[BagItIterator] = None

  val child_collections = getNextGenerationCollections(List(root_collection))

  var collectionCount = 0
  var numCollections = child_collections.size

  var bytesSoFar : Long  = 0L
  var file_type = 0


  private def addCollectionInfoToZip(folderName: String, collection: models.Collection, zip: ZipOutputStream): Option[InputStream] = {
    val path = folderName + "/"+collection.name+"_info.json"
    zip.putNextEntry(new ZipEntry(folderName + "/"+collection.name+"_info.json"))
    val infoListMap = Json.prettyPrint(jsonCollection(collection))
    Some(new ByteArrayInputStream(infoListMap.getBytes("UTF-8")))
  }

  // TODO: Repeat from api/Collections
  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description,
      "created" -> collection.created.toString,"author"-> collection.author.email.toString, "root_flag" -> collections.hasRoot(collection).toString,
      "child_collection_ids"-> collection.child_collection_ids.mkString(","), "parent_collection_ids" -> collection.parent_collection_ids.mkString(","),
      "childCollectionsCount" -> collection.childCollectionsCount.toString, "datasetCount"-> collection.datasetCount.toString, "spaces" -> collection.spaces.mkString(",")))
  }


  private def addCollectionMetadataToZip(folderName : String , collection : models.Collection, zip : ZipOutputStream) : Option[InputStream] = {
    val path = folderName+"/"+collection.name+"_metadata.json"
    zip.putNextEntry(new ZipEntry(folderName+"/"+collection.name+"_metadata.json"))
    val collectionMetadata = getCollectionInfoAsJson(collection)
    val metadataMap = Json.prettyPrint(collectionMetadata)
    Some(new ByteArrayInputStream(metadataMap.getBytes("UTF-8")))
  }

  def getCollectionInfoAsJson(collection : models.Collection) : JsValue = {
    val author = collection.author.fullName
    Json.obj("description"->collection.description,"created"->collection.created.toString)
  }

  def getCollectionMetadataAsJson(collection : models.Collection) : JsValue = {
    val collectionMetadata = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.collection, collection.id)).map(JSONLD.jsonMetadataWithContext(_))
    Json.obj("metadata"->collectionMetadata)
  }

  // TODO: Repeat from api/Collections
  def getNextGenerationCollections(currentCollections : List[Collection]) : List[Collection] = {
    var nextGenerationCollections : ListBuffer[Collection] = ListBuffer.empty[Collection]
    for (currentCollection <- currentCollections){
      collections.get(currentCollection.child_collection_ids).found.foreach(child_col => {
        nextGenerationCollections += child_col
      })
    }
    nextGenerationCollections.toList
  }

  def setBytes(totalBytes : Long) = {
    bytesSoFar = totalBytes
    bagItIterator match {
      case Some(bagIterator) => bagIterator.setBytes(bytesSoFar)
      case None =>
    }
  }

  def isBagIt() = {
    if (file_type == 4){
      true
    } else {
      false
    }
  }

  def hasNext() = {
    if (file_type < 2){
      true
    }
    else if (file_type ==2){
      if (datasetIterator.hasNext()){
        true
      } else if (numCollections > 0){

        currentCollectionIterator = Some(new CollectionIterator(pathToFolder+"/"+child_collections(collectionCount).name, child_collections(collectionCount),zip,md5Files,user,
          collections,datasets,files,
          folders,metadataService,spaces))
        file_type +=1
        true
      } else if (bagit){
        bagItIterator = Some(new BagItIterator(pathToFolder,Some(root_collection) ,zip,md5Bag,md5Files,bytesSoFar ,user))
        file_type = 4
        true
      } else {
        false
      }
    } else if (file_type == 3) {
      currentCollectionIterator match {
        case Some(collectionIterator) => {
          if (collectionIterator.hasNext()){
            true
          } else if (collectionCount < numCollections -2){
            collectionCount+=1
            currentCollectionIterator = Some(new CollectionIterator(pathToFolder+"/"+child_collections(collectionCount).name, child_collections(collectionCount),zip,md5Files,user,
              collections,datasets,files,
              folders,metadataService,spaces))
            true
          } else {
            if (bagit){
              bagItIterator = Some(new BagItIterator(pathToFolder,Some(root_collection) ,zip,md5Bag,md5Files,bytesSoFar ,user))
              file_type+=1
              true
            } else {
              false
            }

          }
        }
        case None => false
      }
    } else if (file_type == 4 && bagit){
      bagItIterator match {
        case Some(bagIterator) => {
          if (bagIterator.hasNext()){
            true
          } else {
            file_type +=1
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
        val is = addCollectionInfoToZip(pathToFolder, root_collection,zip)
        file_type+=1
        Some(new DigestInputStream(is.get, md5))
      }
      //collection metadata
      case 1 => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(pathToFolder+"_metadata.json",md5)
        val is = addCollectionMetadataToZip(pathToFolder, root_collection,zip)
        file_type+=1
        Some(new DigestInputStream(is.get, md5))
      }
      //datasets in this collection
      case 2 => {
        datasetIterator.next()
      }
      //sub collections
      case 3 => {
        currentCollectionIterator match {
          case Some(collectionIterator) => collectionIterator.next()
          case None => None
        }
      }
      //bag it
      case 4 => {
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
