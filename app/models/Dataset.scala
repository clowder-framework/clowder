package models

import com.mongodb.casbah.Imports._
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import java.util.Date
import securesocial.core.Identity
import services.mongodb.MongoSalatPlugin

/**
 * A dataset is a collection of files, and streams.
 *
 *
 * @author Luigi Marini
 *
 */
case class Dataset(
  id: ObjectId = new ObjectId,
  name: String = "N/A",
  author: Identity,
  description: String = "N/A",
  created: Date,
  files: List[File] = List.empty,
  streams_id: List[ObjectId] = List.empty,
  tags: List[Tag] = List.empty,
  metadata: Map[String, Any] = Map.empty,
  userMetadata: Map[String, Any] = Map.empty,
  collections: List[String] = List.empty,
  thumbnail_id: Option[String] = None,
  datasetXmlMetadata: List[DatasetXMLMetadata] = List.empty,
  userMetadataWasModified: Option[Boolean] = None)

object MustBreak extends Exception {}

object Dataset extends ModelCompanion[Dataset, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Dataset, ObjectId](collection = x.collection("datasets")) {}
  }
}
