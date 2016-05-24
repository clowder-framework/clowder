package models

import java.util.Date

/**
 * A folder
 */
case class Folder (
    id: UUID = UUID.generate,
    created: Date,
    author: MiniUser,
    name: String = "N/A",
    displayName: String = "N/A",
    files: List[UUID] = List.empty,
    folders: List[UUID] = List.empty,
    parentId: UUID,
    parentType: String,
    parentDatasetId: UUID)

