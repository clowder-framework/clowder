package models

import com.mongodb.casbah.Imports._
import java.util.Date
import play.api.libs.json.{Writes, Json}
import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
 * A dataset is a collection of files, and streams.
 */
case class Dataset(
  id: UUID = UUID.generate,
  name: String = "N/A",
  author: MiniUser,
  description: String = "N/A",
  created: Date,
  files: List[UUID] = List.empty,
  folders: List[UUID] = List.empty,
  streams_id: List[ObjectId] = List.empty,
  tags: List[Tag] = List.empty,
  metadataCount: Long = 0,
  collections: List[UUID] = List.empty,
  thumbnail_id: Option[String] = None,
  licenseData: LicenseData = new LicenseData(),
  spaces: List[UUID] = List.empty,
  lastModifiedDate: Date = new Date(),
  trash : Boolean = false,
  dateMovedToTrash : Option[Date] = None,
  followers: List[UUID] = List.empty,
  stats: Statistics = new Statistics(),
  status: String = DatasetStatus.PRIVATE.toString, // dataset has four status: trial, default, private and public. yet editors of the dataset
  // can only see the default, private and public, where trial equals to private. viewers can only see private and
  // public, where trial and default equals to private/public of its space
  creators: List[String] = List.empty
){
  def isPublic:Boolean = status == DatasetStatus.PUBLIC.toString
  def isDefault:Boolean = status == DatasetStatus.DEFAULT.toString
  def isTRIAL:Boolean = status == DatasetStatus.TRIAL.toString
  def inSpace:Boolean = spaces.size > 0
}

object DatasetStatus extends Enumeration {
  type DatasetStatus = Value
  val PUBLIC, PRIVATE, DEFAULT, TRIAL = Value
}


object Dataset {
  implicit val datasetWrites = new Writes[Dataset] {
    def writes(dataset: Dataset): JsValue = {
      val datasetThumbnail = if(dataset.thumbnail_id.isEmpty) {
        null
      } else {
        dataset.thumbnail_id.toString().substring(5,dataset.thumbnail_id.toString().length-1)
      }
      Json.obj(
        "id" -> dataset.id.toString,
        "name" -> dataset.name,
        "description" -> dataset.description,
        "created" -> dataset.created.toString,
        "thumbnail" -> datasetThumbnail,
        "authorId" -> dataset.author.id,
        "spaces" -> dataset.spaces,
        "resource_type" -> "dataset")
    }
  }
}


case class DatasetAccess(
  showAccess:  Boolean = false,
  access: String = "N/A",
  accessOptions: List[String] = List.empty
)