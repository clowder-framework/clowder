package models

/**
 * A folder
 */
case class Folder (
    id: UUID = UUID.generate,
    name: String = "N/A",
    displayName: String = "N/A",
    files: List[UUID] = List.empty,
    folders: List[UUID] = List.empty,
    parentId: UUID,
    parentType: String,
    parentDatasetId: UUID)

