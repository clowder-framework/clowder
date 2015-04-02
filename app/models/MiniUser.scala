package models

/**
 * Class to contain a subset of User data for fast loading.
 *
 * @author Will Hennessy
 */

 case class MiniUser(
  id: UUID,
  fullName: String,
  avatarURL: String)