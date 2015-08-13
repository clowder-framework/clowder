package models

import java.util.Date
import securesocial.core.Identity

/**
 * Created by yanzhao3 on 8/12/15.
 */
case class CurationObj (
  id: UUID = UUID.generate,
  name: String = "N/A",
  author: Option[Identity],
  description: String = "N/A",
  created: Date,
  spaces: UUID,
  datasets: List[Dataset] =  List.empty,
  collections: List[Collection] = List.empty
)



