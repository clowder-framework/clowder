package models

/**
 * Track relations between two arbitrary resources.
 *
 * @author Luigi Marini
 *
 */
case class Relation (
  id: UUID = UUID.generate,
  source: Node,
  target: Node,
  rdfType: Option[String] = None // rdfType: Option[URI]
)

case class Node (
  id: String,
  resourceType: ResourceType.Value
)



