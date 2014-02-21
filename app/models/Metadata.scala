/**
 *
 */
package models

/**
 * Single piece of metadata.
 *
 * @author Luigi Marini
 *
 */
case class Metadata(
  key: String,
  value: String,
  predicate: Option[String])