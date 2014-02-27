package models

import java.util.Date
import com.novus.salat.dao.ModelCompanion
import MongoContext.context
import play.api.Play.current
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._
import services.mongodb.MongoSalatPlugin

/**
 * A portion of a file.
 *
 * @author Luigi Marini
 *
 */
case class Section(
  id: ObjectId = new ObjectId,
  file_id: ObjectId = new ObjectId,
  order: Int = -1,
  startTime: Option[Int] = None, // in seconds
  endTime: Option[Int] = None, // in seconds
  area: Option[Rectangle] = None,
  preview: Option[Preview] = None,
  tags: List[Tag] = List.empty)

case class Rectangle(
  x: Double,
  y: Double,
  w: Double,
  h: Double) {
  override def toString() = f"[ $x%.3f, $y%.3f, $w%.3f, $h%.3f ]"
}