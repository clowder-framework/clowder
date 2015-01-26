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
  def count(filter: Option[Map[String, String]] = None): Long

  /**
   * Return a list objects that are available based on the filter as well as the other options.
   *
   * @param start the first element that should be returned based on the order key
   * @param limit the maximum number of elements to return
   * @param order the key to use to order the data, default is natural ordering of underlying implementation
   * @param direction the direction to order the data in
   * @param filter is a map of values that are used to filter, a * can be used to indicate any sequence of characters,
   *               if the value is NULL it means the key should be absent
   *
   */
  def list(start: Option[String] = None, limit: Integer = 20,
           order: Option[String] = None, direction: Direction=DESC,
           filter: Option[Map[String, String]] = None): List[A]
}
