package util

import models.MultimediaFeatures
import play.Logger
import services.{DI, MultimediaQueryService}

/**
 * Methods to manipulate the local multimedia index.
 */
object MultimediaIndex {

  val queries: MultimediaQueryService = DI.injector.getInstance(classOf[MultimediaQueryService])

  def recomputeAllDistances(): Unit = {
    Logger.debug("Recomputing all feature distances")
    queries.recomputeAllDistances()
  }

  def computeDistancesForSection(section: MultimediaFeatures): Unit = {
    Logger.debug(s"Computing distances for section ${section.section_id}")
    queries.computeDistances(section)
  }

  def searchPrecomputedDistances(querySectionId: String, representation: String, limit: Int): Unit = {
    Logger.debug(s"Query for section $querySectionId against index $representation found the following results:")
    // Would need to provide appropriate Space IDs to the method below to get result
    queries.searchMultimediaDistances(querySectionId, representation, limit, List.empty).foreach { d =>
      Logger.debug(s"${d.source_section} -> ${d.target_section} = ${d.distance}")
    }
  }
}
