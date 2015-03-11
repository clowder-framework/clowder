package services

import models.{UUID, Relation}

/**
 * Track relations between resources
 *
 * @author Luigi Marini
 *
 */
trait RelationService {

  def list(): List[Relation]

  def get(id: UUID): Option[Relation]

  def add(relation: Relation): Option[UUID]

  def delete(id: UUID)
}
