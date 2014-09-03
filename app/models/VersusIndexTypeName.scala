package models

import play.api.libs.json._
import play.api.libs.json.Reads
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsResult

import play.api.libs.json.Json

//names of the variables here MUST be the same as in versus IndexResource.listJSON, where the Json is created.
case class VersusIndexTypeName(
   indexID: String, MIMEtype:String,  Extractor: String, Measure:String, Indexer:String, indexName: Option[String], indexType:Option[String])

object VersusIndexTypeName {
    implicit val format: Format[VersusIndexTypeName] = Json.format[VersusIndexTypeName]

    def addName(index: VersusIndexTypeName, name:String): VersusIndexTypeName = {
        index.copy(indexName = Some(name))
    }
}

/**
   * Creates a Format[T] by resolving case class fields & required implicits at COMPILE-time
   *
   * If any missing implicit is discovered, compiler will break with corresponding error.
   * {{{
   *   import play.api.libs.json.Json
   *
   *   case class User(name: String, age: Int)
   *
   *   implicit val userWrites = Json.format[User]
   *   // macro-compiler replaces Json.format[User] by injecting into compile chain
   *   // the exact code you would write yourself. This is strictly equivalent to:
   *   implicit val userWrites = (
   *      (__ \ 'name).format[String] and
   *      (__ \ 'age).format[Int]
   *   )(User.apply, unlift(User.unapply))
   * }}}
   */