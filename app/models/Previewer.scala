package models

/**
 * Previewers are javascripts library to visualize information on the web interface.
 *
 * @author Luigi Marini
 */
case class Previewer(
  id: String,
  path: String,
  main: String,
  contentType: List[String])