package models

import com.mongodb.casbah.Imports._
import java.util.Date
import play.api.libs.json.{Writes, Json}
import play.api.libs.json._
import play.api.libs.functional.syntax._

//import util.Formatters
//package object models {
//   def cap (l: List[Any]) : List[Any] = { 
      //return (l.length < 2 ? l : (l.take(2) :: List("..."))) 
      //return (l.length < 2 ? l : l.take(2) :: List("...")) 
//         return l.take(2) } }
//import java.time.LocalDateTime
//import java.time.format.DateTimeFormatter._
//from Formatters
import java.text.SimpleDateFormat
import java.util.Date

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
   def cap (l: List[Any], max: Int) : List[Any] = { 
      return l.take(max) 
      //return (l.length < max ? l : l.take(max) :: "...") 
      //if (l.length < max)  return l  
      //   else { return  l.take(max) :: "¨" }
   } 
 // def cap_map (l: List[Any], max: Int, λ: (A) -> String ) : List[Any] = {  //pass in lambda so don't have2rewrite
 //   if (l.length < max)  return l  
 //      else { return  l.take(max).map(λ) :: "¨" } }  //want to append to mapped list, so as not to map the ...
      //write specific1before generalizing as above
   def cap_files (l: List[UUID], max: Int, URLb: String) : List[String] = {   //ret L of Strings for Json serializer
   //def cap_files (l: List[Any], max: Int, URLb: String) : List[String] = {   
      if (l.length < max)  {
        //return files.map(f => URLb + "/files/" + f)
        return l.map(f => URLb + "/files/" + f) //this case works
      } else {
         val cl = l.take(max)
         //cl +=  "..." 
         //val cl = l.take(max) :: "..." //oh yes has2be a list
         //val cl = l.take(max) :: List("...")
         //   System.out.println(cl)
//       val sl = cap(files, max).map(f => URLb + "/files/" + f)
           //return sl :: "..."  //value :: is not a member of String  //expected it was a list
//       return sl 
      //  return cl.map(f => URLb + "/files/" + f) :: "..." //same error
      //  return cl.map(f => URLb + "/files/" + f) //:: List("...") //type mismatch; found : List[String] required: String
          val r : List[String] = cl.map(f => URLb + "/files/" + f) //:: "..." 
          //r += "..."
          //return r.::("...") //was an insert vs append
          //return List("...")::r
          val ls : List[String]=List("...")
          //return ls :+ r //type mismatch; found : List[Object] required: List[String]
          //return r :+ ls
          //return ls.::r
          return r.::("...").reverse //was an insert vs append
      }
   }
  /**
    * return Dataset as JsValue in jsonld format
    */
  def to_jsonld(url: String) : JsValue = { 
     val so = JsObject(Seq("@vocab" -> JsString("https://schema.org/")))
     val URLb = url.replaceAll("/$", "") 
     val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
     val datasetLD = Json.obj(
              "context" -> so,
              "identifier" -> id.toString,
              "name" -> name,
              "author" -> author.to_jsonld(),
              "description" -> description,
              "dateCreated" -> formatter.format(created), //iso8601,incl tz
              //for all lists, cap, ... //if >10 replace last w/"..."
              //"DigitalDocument" -> Json.toJson(files.map(f => URLb + "/files/" + f)), 
              //"DigitalDocument" -> Json.toJson(url.replaceAll("/$", "") + "/api/datasets/" + id.toString + "/files?max=9"),
              //"DigitalDocument" -> Json.toJson(files.take(2).map(f => URLb + "/files/" + f)), //limits but needs append "..."
              //"DigitalDocument" -> Json.toJson(cap(files, 3).map(f => URLb + "/files/" + f)), //2 for testing
              "DigitalDocument" -> Json.toJson(cap_files(files, 3, URLb)), //3 for testing
              //"Directory" -> Json.toJson(folders), //skip
              "Collection" -> Json.toJson(collections), //like w/file urls, &below, 
              //"thumbnail" -> Json.toJson((thumbnail_id == null ? "" : URlb + thumbnail_id)), 
              "thumbnail" -> Json.toJson(URLb + thumbnail_id.getOrElse("")), //get url, skip append in null/fix
              "license" -> licenseData.to_jsonld(),
              "dateModfied" -> formatter.format(lastModifiedDate),
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
