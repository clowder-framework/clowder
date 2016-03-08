package models

/**
 * Class to contain a subset of User data for fast loading.
 */

 case class MiniUser(
  id: UUID,
  fullName: String,
  avatarURL: String,
  email: Option[String])