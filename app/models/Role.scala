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
  permissions: Set[String] = Set.empty
)
