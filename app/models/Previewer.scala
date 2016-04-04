package models

/**
 * Previewers are javascripts library to visualize information on the web interface.
 *
 */
case class Previewer(
  id: String,
  path: String,
  main: String,
  contentType: List[String],
  supportedPreviews: List[String],
  collection: Boolean = false,
  dataset: Boolean = false)
