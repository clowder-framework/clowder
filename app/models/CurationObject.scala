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
  submittedDate: Option[Date],
  publishedDate: Option[Date],
  space: UUID,
  datasets: List[Dataset] =  List.empty,
  collections: List[Collection] = List.empty,
  status: String
)



