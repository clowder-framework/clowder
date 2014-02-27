package models

import com.mongodb.casbah.Imports._

/**
 * Tracks application wide configurations.
 *
 * @author Luigi Marini
 *
 */
case class AppConfiguration(
  id: ObjectId = new ObjectId,
  name: String = "default",
  theme: String = "bootstrap/bootstrap.css")

