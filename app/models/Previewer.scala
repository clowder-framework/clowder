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
  file: Boolean = false,
  preview: Boolean = false,
  dataset: Boolean = false,
  collection: Boolean = false)

