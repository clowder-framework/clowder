package Iterators

import java.io.InputStream
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.ZipOutputStream

import javax.inject.Inject

import scala.collection.mutable.HashMap
import play.api.libs.json.Json._
import play.api.libs.json.{JsValue, Json}
import models.{Collection, User}
import services._

/**
 * This is used to download a collection.
 *
 * @param pathToFolder - base path into which collection contents will be written inside the output zip file
 * @param collection - the collection to be downloaded
 * @param zip - a ZipOutputStream that will receive contents and metadata of the collection
 * @param md5Files - HashMap for MD5 checksums of all data files that go into the zip
 * @param md5Bag - HashMap for MD5 checksums of all BagIt metadata files that go into the zip
 * @param user - if a user is specified, datasets will be limited to their permissions
 * @param bagit - whether or not to include BagIt metadata in the output
 */
class CollectionIterator (pathToFolder: String, collection: Collection, zip: ZipOutputStream,
                              md5Files: HashMap[String, MessageDigest], md5Bag: HashMap[String, MessageDigest],
                              user: Option[User], bagit: Boolean) extends Iterator[Option[InputStream]] {

  // Guice injection doesn't work in the context we're using this in
  val collections = DI.injector.getInstance(classOf[CollectionService])
  val datasets = DI.injector.getInstance(classOf[DatasetService])
  val metadatas = DI.injector.getInstance(classOf[MetadataService])
  val files = DI.injector.getInstance(classOf[FileService])
  val folders = DI.injector.getInstance(classOf[FolderService])
  val spaces = DI.injector.getInstance(classOf[SpaceService])

  val datasetIterator = new DatasetsInCollectionIterator(pathToFolder+"/"+collection.name, collection, zip, md5Files, user)
  var bagItIterator: Option[BagItIterator] = None
  val childCollections = collections.get(collection.child_collection_ids).found
  var currentCollection: Option[Collection] = None
  var currentCollectionIterator: Option[CollectionIterator] = None
  var currentCollectionIdx = 0
  var bytesSoFar = 0L
  var file_type = "collection_info"


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
      case "collection_info" => true
      case "collection_metadata" => true
      case "dataset_iterator" => {
        if (datasetIterator.hasNext()) true
        else if (childCollections.size > 0) {
          currentCollection = Some(childCollections(currentCollectionIdx))
          currentCollectionIterator = Some(new CollectionIterator(pathToFolder + "/" + currentCollection.get.name,
            currentCollection.get, zip, md5Files, md5Bag, user, false))
          file_type = "child_collections"
          true
        } else if (bagit) {
          bagItIterator = Some(new BagItIterator(pathToFolder, zip, collection.id.stringify, collection.description,
            md5Bag, md5Files, bytesSoFar, user))
          file_type = "bagit"
          true
        } else false
      }
      case "child_collections" => {
        currentCollectionIterator match {
          case Some(collectionIterator) => {
            if (collectionIterator.hasNext()) true
            else if (currentCollectionIdx < childCollections.size-2) {
              // TODO: Why size -2 and not 1 in line above? Parent collection omitted?
              currentCollectionIdx += 1
              currentCollection = Some(childCollections(currentCollectionIdx))
              currentCollectionIterator = Some(new CollectionIterator(pathToFolder+"/"+currentCollection.get.name,
                currentCollection.get, zip, md5Files, md5Bag, user, false))
              true
            } else if (bagit) {
              bagItIterator = Some(new BagItIterator(pathToFolder, zip, collection.id.stringify, collection.description,
                md5Bag, md5Files, user))
              file_type = "bagit"
              true
            } else false
          }
          case None => false
        }
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
      case "collection_info" => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(pathToFolder + "_info.json", md5)
        val is = IteratorUtils.addJsonFileToZip(zip, pathToFolder, collection.name+"_info",
          IteratorUtils.getCollectionInfoAsJson(collection))
        file_type = "collection_metadata"
        Some(new DigestInputStream(is.get, md5))
      }
      case "collection_metadata" => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(pathToFolder + "_metadata.json", md5)
        // TODO: How is _metadata distinct from _info.json? Is this required (we don't support collection metadata)?
        val metadataMap = Json.obj(
          "author" -> collection.author.email.getOrElse(""),
          "description" -> collection.description,
          "created" -> collection.created.toString
        )
        val is = IteratorUtils.addJsonFileToZip(zip, pathToFolder, collection.name+"_metadata", metadataMap)
        file_type = "dataset_iterator"
        Some(new DigestInputStream(is.get, md5))
      }
      case "dataset_iterator" => datasetIterator.next()
      case "child_collections" => {
        currentCollectionIterator match {
          case Some(collectionIterator) => collectionIterator.next()
          case None => None
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
  }
}
