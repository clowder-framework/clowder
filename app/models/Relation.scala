package models

import java.net.URL

/**
 * Track relations between two arbitrary resources.
 */
case class Relation (
  id: UUID = UUID.generate,
  source: Node,
  target: Node,
  rdfType: Option[String] = None // rdfType: Option[URI]
)

/**
  * Source or sink node of a relationship.
  *
  * @param id a string so that it could be both a local UIUD as well as an external URL
  * @param resourceType internal resource type (dataset, file, etc.)
  */
case class Node (
  id: String,
  resourceType: ResourceType.Value
)

case class NodeDataset(dataset: Dataset, rdfType: Option[String])

case class NodeFile(file: File, rdfType: Option[String])

case class NodeURL(url: URL, rdfType: Option[String])


