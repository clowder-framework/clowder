package models

import java.util.Date
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.Imports._
import MongoContext.context
import play.api.Play.current
import services.mongodb.MongoSalatPlugin

case class Collection(
  id: ObjectId = new ObjectId,
  name: String = "N/A",
  description: String = "N/A",
  created: Date,
  datasets: List[Dataset] = List.empty,
  thumbnail_id: Option[String] = None)

object Collection extends ModelCompanion[Collection, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Collection, ObjectId](collection = x.collection("collections")) {}
  }
}