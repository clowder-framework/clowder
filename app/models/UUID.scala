package models

import org.bson.types.ObjectId
import com.novus.salat.transformers.CustomTransformer
import play.api.Logger

/**
 * Created by lmarini on 2/20/14.
 */
class UUID(uuid: String) {
  def stringify = uuid
  override def toString() = uuid
}

object UUID {

  def apply(uuid: String): UUID = {
    new UUID(new ObjectId(uuid).toString)
  }

  def apply(): UUID = {
    new UUID(new ObjectId().toString)
  }

  def generate(): UUID = apply()

  def isValid(uuid: UUID): Boolean = {
    ObjectId.isValid(uuid.stringify)
  }
}

object UUIDConversions {
  implicit def stringToUUID(s: String) = UUID(s)
}

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
