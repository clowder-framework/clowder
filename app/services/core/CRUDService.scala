package services.core

import util.Direction._
import models.UUID

/**
 * Basic CRUD functionality for services manipulating resources.
 * Mostly to enforce similar signatures across services.
 *
 * @author Luigi Marini
 * @author Rob Kooper
 *
 */
trait CRUDService[A] {

  def get(id: UUID): Option[A]

  def insert(model: A): Option[String]

  def update(model: A)

  def delete(id: UUID)

  /**
   * The number of objects that are available based on the filter
   */
  def count(filter: Option[String] = None): Long

  /**
   * Return a list objects that are available based on the filter as well as the other options.
   *
   * @param order the key to use to order the data, default is natural ordering of underlying implementation
   * @param direction the direction to order the data in
   * @param start the first element that should be returned based on the order key
   * @param limit the maximum number of elements to return
   * @param filter is a json representation of the filter to be applied
   *
   */
  def list(order: Option[String] = None, direction: Direction=DESC,
           start: Option[String] = None, limit: Integer = 20,
           filter: Option[String] = None): List[A]
}
