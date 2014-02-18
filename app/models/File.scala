package models

import java.util.Date
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.Imports._
import securesocial.core.Identity

import services.mongodb.MongoSalatPlugin

/**
 * Uploaded files.
 *
 * @author Luigi Marini
 *
 */
case class File(
  id: ObjectId = new ObjectId,
  path: Option[String] = None,
  filename: String,
  author: Identity,
  uploadDate: Date,
  contentType: String,
  length: Long = 0,
  showPreviews: String = "DatasetLevel",
  sections: List[Section] = List.empty,
  previews: List[Preview] = List.empty,
  tags: List[Tag] = List.empty,
  metadata: List[Map[String, Any]] = List.empty,
  thumbnail_id: Option[String] = None,
  isIntermediate: Option[Boolean] = None,
  userMetadata: Map[String, Any] = Map.empty,
  xmlMetadata: Map[String, Any] = Map.empty,
  userMetadataWasModified: Option[Boolean] = None)

object FileDAO extends ModelCompanion[File, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[File, ObjectId](collection = x.collection("uploads.files")) {}
  }
}
