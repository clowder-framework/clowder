package models

import java.util.Date

import play.api.libs.json.{Json, JsValue, Writes}
import securesocial.core.Identity

case class Logo(id: UUID = UUID.generate,
                use: String = "",
                showText: Boolean = true,
                path: Option[String] = None,
                filename: String,
                author: Identity,
                uploadDate: Date,
                contentType: String,
                length: Long = 0,
                sha512: String = "",
                loader: String = "")

object Logo {
  implicit val logostWrites = new Writes[Logo] {
    def writes(logo: Logo): JsValue = {
      Json.obj("id" -> logo.id.toString,
        "path" -> logo.use,
        "name" -> logo.filename,
        "showText" -> logo.showText,
        "length" -> logo.length,
        "content-type" -> logo.contentType,
        "created" -> logo.uploadDate.toString,
        "authorId" -> logo.author.identityId.userId)
    }
  }
}
