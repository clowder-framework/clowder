package models

import com.mongodb.casbah.Imports._
import java.util.Date
import play.api.libs.json.{Writes, Json}
import play.api.libs.json._
import play.api.libs.functional.syntax._

//def cap (l: List, n: Int) : List = {
//def cap (l: List) : List = { return (l.length < 2 ? l : (l.take(2) :: List("..."))) }

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
  /**
    * return Dataset as JsValue in jsonld format
    */
  def to_jsonld(url: String) : JsValue = { 
     val so = JsObject(Seq("@vocab" -> JsString("https://schema.org/")))
     val URLb = url.replaceAll("/$", "") 
     val datasetLD = Json.obj(
              "context" -> so,
              "identifier" -> id.toString,
              "name" -> name,
              "author" -> author.to_jsonld(),
              "description" -> description,
              //"dateCreated" -> created.toString.format("MMM dd, yyyy"), //iso8601,incl tz
              "dateCreated" -> created.toString, //iso8601,incl tz
              //for all lists, cap, ... //if >10 replace last w/"..."
              //"DigitalDocument" -> Json.toJson(files.map(f => URLb + "/files/" + f)), 
              //"DigitalDocument" -> Json.toJson(cap(files).map(f => URLb + "/files/" + f)), //2 for testing
              "DigitalDocument" -> Json.toJson(files.take(2).map(f => URLb + "/files/" + f)),
              //"DigitalDocument" -> Json.toJson(url.replaceAll("/$", "") + "/api/datasets/" + id.toString + "/files?max=9"),
              //"Directory" -> Json.toJson(folders), //skip
              "Collection" -> Json.toJson(collections), //like w/file urls, &below, 
              "thumbnail" -> Json.toJson(URLb + thumbnail_id.getOrElse("")), //get url
              //"thumbnail" -> Json.toJson((thumbnail_id == null ? "" : URlb + thumbnail_id)), 
              "license" -> licenseData.to_jsonld(),
              //"dateModfied" -> lastModifiedDate.toString.format("MMM dd, yyyy"),
              "dateModfied" -> lastModifiedDate.toString,
              //"FollowAction" -> Json.toJson(followers), //skip
              "keywords" -> tags.map(x => x.to_jsonld()),
              "creator" -> Json.toJson(creators)
              )
        return datasetLD
     }
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
