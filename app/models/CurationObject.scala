package models

import java.util.Date
import securesocial.core.Identity

/**
 * A Curation Object assists researchers and curators to identify sets of resources for publication.
 */
case class CurationObject (
  id: UUID = UUID.generate,
  name: String = "",
  author: Identity,
  description: String = "",
  created: Date,
  space: UUID,
  datasets: List[Dataset] =  List.empty,
  collections: List[Collection] = List.empty,
  //here we don't use a map to know the file belongs to which dataset, we find the fileByDataset from dataset.files._.id
  files: List[File] =  List.empty,
  repository: Option[String] = Some(""),
  submitted: Option[Boolean] = Some(false)
)



