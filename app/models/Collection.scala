package models

import java.util.Date
import com.mongodb.casbah.Imports._

case class Collection(
  id: ObjectId = new ObjectId,
  name: String = "N/A",
  description: String = "N/A",
  created: Date,
  datasets: List[Dataset] = List.empty,
  thumbnail_id: Option[String] = None)