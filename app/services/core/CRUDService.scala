package services.core

import models.UUID

/**
 * Basic CRUD functionality for services manipulating resources.
 * Mostly to enforce similar signatures across services.
 *
 * @author Luigi Marini
 *
 */
trait CRUDService[A] {

  def get(id: UUID): Option[A]

  def insert(model: A): Option[String]

  def update(model: A)

  def delete(id: UUID)

  def list(): List[A]
}
