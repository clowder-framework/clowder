package models

import com.mongodb.casbah.Imports._
import java.util.Date
import play.api.libs.json.{Writes, Json}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import _root_.util.Formatters

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
    * Caps a list at 'max'  
    * then turns it's ID's into resolvable URLs of that 'apiRoute' type
    * end with appending "..." to the List, to signify that it was abridged 
    *
    * todo: issue 354 to the max configurable
    */
  def cap_api_list (l: List[UUID], max: Int, URLb: String, apiRoute: String) : List[String] = {  
      if (l.length <= max)  {
        return l.map(f => URLb + apiRoute + f) 
      } else {
         val cl = l.take(max)
         val r : List[String] = cl.map(f => URLb + apiRoute + f) 
         return r.::("...").reverse 
      }
   } 

  /**
    * return Dataset as JsValue in jsonld format
    */
  def to_jsonld(url: String) : JsValue = { 
     val so = JsObject(Seq("@vocab" -> JsString("https://schema.org/")))
     val URLb = url.replaceAll("/$", "") 
     var pic_id = thumbnail_id.getOrElse("")
     if (pic_id != "") {
        pic_id = URLb + pic_id 
     } else ""
     val datasetLD = Json.obj(
           "context" -> so,
           "identifier" -> id.toString,
           "name" -> name,
           "author" -> author.to_jsonld(),
           "description" -> description,
           "dateCreated" -> Formatters.iso8601(created), 
           "DigitalDocument" -> Json.toJson(cap_api_list(files, 10, URLb, "/files/")), 
           //"Directory" -> Json.toJson(folders), //skip
           //"FollowAction" -> Json.toJson(followers), //skip
           //"Collection"->Json.toJson(cap_api_list(collections,1,URLb,"/collections/")), //skip
           //earthcube used spaces, as a repo's 'DataCatalog', but better to have
           // 'space' as so:Collection
           "Collection" -> Json.toJson(cap_api_list(spaces, 10, URLb, "/spaces/")), 
           "thumbnail" -> Json.toJson(pic_id),
           "license" -> licenseData.to_jsonld(),
           "dateModfied" -> Formatters.iso8601(lastModifiedDate),
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
