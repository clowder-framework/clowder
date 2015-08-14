package models

import java.util.Date
import securesocial.core.Identity

/**
 * A Curation Object assists researchers and curators to identify sets of resources for publication.
 */
case class CurationObj (
  id: UUID = UUID.generate,
  name: String = "",
  author: Identity,
  description: String = "",
  created: Date,
  space: UUID,
  datasets: List[Dataset] =  List.empty,
  collections: List[Collection] = List.empty
)



