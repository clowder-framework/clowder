package models

import api.Permission.Permission

/**
 * A role is a set of permissions. Permissions are defined in api.Permissions.
 * Roles are defined by system administrators.
 *
 * @author Luigi Marini
 */
case class Role(
  id: UUID = UUID.generate,
  name: String = "N/A",
  description: String = "N/A",
  permissions: List[String] = List.empty
)

object Role {
  val roleList: List[String] = List("Admin", "Power User", "User", "Guest")
  var roleMap: Map[String, Role] = Map.empty
  for (aRole <- roleList) {
    roleMap += (aRole -> Role(name = aRole))
  }
}
