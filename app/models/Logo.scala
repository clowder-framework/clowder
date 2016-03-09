package models

import java.util.Date

import play.api.libs.json.{Json, JsValue, Writes}

case class Logo(id: UUID = UUID.generate,
                loader_id: String,
                sha512: String,
                length: Long,
                loader: String,
                path: String,
                name: String,
                contentType: String,
                author: User,
                uploadDate: Date = new Date(),
                showText: Boolean = true)

object Logo {
  implicit val logostWrites = new Writes[Logo] {
    def writes(logo: Logo): JsValue = {
      Json.obj("id" -> logo.id.toString,
        "sha512" -> logo.sha512,
        "length" -> logo.length,
        "path" -> logo.path,
        "name" -> logo.name,
        "content-type" -> logo.contentType,
        "created" -> logo.uploadDate.toString,
        "author" -> logo.author.id,
        "showText" -> logo.showText)
    }
  }
}
