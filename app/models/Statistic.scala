package models

import java.util.Date
import play.api.libs.json.Json._
import play.api.libs.json._


case class Statistics (
  views: Int = 0,
  downloads: Int = 0,
  last_viewed: Option[Date] = None,
  last_downloaded: Option[Date] = None
)

case class StatisticUser (
  user_id: UUID,
  resource_id: UUID,
  resource_type: String,
  views: Int = 0,
  downloads: Int = 0,
  last_viewed: Date = new Date(),
  last_downloaded: Date = new Date()
)
