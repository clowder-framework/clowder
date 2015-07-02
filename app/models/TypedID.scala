package models

import play.api.libs.json._

/**
 * Gives access to the type of a particular UUID
 *
 * @author Varun Kethineedi
 */
case class TypedID(
  id: UUID,
  objectType: String
)

object TypedID {
  implicit val typedIDFormat = Json.format[TypedID]
}
