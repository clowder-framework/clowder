package models

import org.bson.types.ObjectId
import com.novus.salat.transformers.CustomTransformer
import play.api.Logger
import play.api.libs.json._

/**
 * Wrapper for system IDs. Currently defaults to MongoDB ObjectId, but should be made generic in the future.
 */
case class UUID(uuid: String) {
  def stringify = uuid
  override def toString() = uuid
  def equals(uuidToCompare: UUID) = {stringify == uuidToCompare.stringify}
}

object UUID {
  implicit val uuidFormat = new Format[UUID] {
    def reads(json: JsValue): JsResult[UUID] = JsSuccess(new UUID(json.toString()))
    def writes(uuid: UUID): JsValue = Json.toJson(uuid.stringify)
  }

  def apply(): UUID = {
    new UUID(new ObjectId().toString)
  }

  def generate(): UUID = apply()

  def isValid(uuid: String): Boolean = {
    ObjectId.isValid(uuid)
  }
}

// scala conversion
object UUIDConversions {
  implicit def stringToUUID(s: String) = UUID(s)
  implicit def uuidToObjectId(uuid: UUID) = new ObjectId(uuid.stringify)
  implicit def objectIdToUUID(objectId: ObjectId) = UUID(objectId.toString)
}

// Salat transformer
object UUIDTransformer extends CustomTransformer[UUID, ObjectId] {
  def deserialize(objectId: ObjectId) = {
    Logger.trace("Deserializing ObjectId to UUID :" + objectId)
    UUID(objectId.toString)
  }

  def serialize(uuid: UUID) = {
    Logger.trace("Serializing UUID to ObjectId :" + uuid)
    new ObjectId(uuid.stringify)
  }
}
