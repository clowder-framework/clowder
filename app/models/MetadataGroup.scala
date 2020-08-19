package models

import java.util.Date

import play.api.libs.json.{Json, Writes}

case class MetadataGroup (
                           id: UUID = UUID.generate(),
                           creatorId: UUID,
                           label: String,
                           description: String,
                           attachedObjectOwner: Option[UUID],
                           createdAt: Date = new Date(),
                           lastModifiedDate: Date = new Date(),
                           spaces: List[UUID] = List.empty,
                           timeAttachedToObject: Option[Date],
                           attachedTo: Option[ResourceRef],
                           keys: List[String]
                         ) {

}

object MetadataGroup {
  implicit object MetadataGroupWrites extends Writes[MetadataGroup] {
    def writes(metadataGroup: MetadataGroup) = Json.obj(
      "id" -> metadataGroup.id.toString(),
      "label" -> metadataGroup.label,
      "creatorId" -> metadataGroup.creatorId.toString,
      "attachedObjectOwner" -> metadataGroup.attachedObjectOwner.getOrElse("").toString,
      "createdAt" -> metadataGroup.createdAt.toString,
      "spaces" -> metadataGroup.spaces,
      "attachedTo" -> metadataGroup.attachedTo.getOrElse("").toString,
      "keys" -> metadataGroup.keys

    )
  }
}
