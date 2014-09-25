package models

/**
 * Tracks application wide configurations.
 *
 * @author Luigi Marini
 *
 */
case class AppConfiguration(
  id: UUID = UUID.generate,
  name: String = "default",
  theme: String = "bootstrap/bootstrap.css",
  admins: List[String] = List.empty)

