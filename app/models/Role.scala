package models

import api.Permission

/**
 * A role is a set of permissions. Permissions are defined in api.Permissions.
 * Roles are defined by system administrators.
 */
case class Role(
  id: UUID = UUID.generate,
  name: String = "N/A",
  description: String = "N/A",
  permissions: Set[String] = Set.empty
)

object Role {
  // Default roles
  // admin role
  val adminPerm = Permission.values
  val Admin = new Role(name = "Admin", description = "Admin Role", permissions = adminPerm.map(_.toString).toSet)
  // editor role
  val editorPerm = for(perm <- adminPerm if perm.toString.toLowerCase.indexOf("admin") == -1) yield perm
  val Editor = new Role(name="Editor", description="Editor Role", permissions = editorPerm.map(_.toString).toSet)
  // viewer role
  val viewerPerm = Permission.READONLY + Permission.DownloadFiles
  val Viewer = new Role(name="Viewer", description="Viewer Role", permissions = viewerPerm.map(_.toString))
}