package models

import play.api.libs.json._

/**
 * Enumerate type of resources known to the system.
 */
object ResourceType extends Enumeration {
//  type ResourceType = Value
  val dataset, file, collection, user, sensor, stream, folder, metadata = Value

  def isWorkingDay(d: ResourceType.Value) = ! (d == Dataset || d == File)
}

object EnumUtils {
  implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
    def writes(v: E#Value): JsValue = JsString(v.toString)
  }
  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
    def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) => {
        try {
          JsSuccess(enum.withName(s))
        } catch {
          case _: NoSuchElementException =>
            JsError(s"Enumeration expected of type: '${enum.getClass}', but it does not appear to contain the value: '$s'")
        }
      }
      case _ => JsError("String value expected")
    }
  }
}

