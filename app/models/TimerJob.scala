package models
import java.util.Date



/**
 * Job to be put into scheduler
 *
 * @author Varun Kethineedi
 */
case class TimerJob(
  name: String,
  minute: Option[Integer] = None,
  hour: Option[Integer] = None,
  day_of_month: Option[Integer] = None,
  day_of_week: Option[Integer] = None,
  function: Option[String] = None,
  parameters: Option[UUID] = None,
  frequency: Option[String] = None,
  lastJobTime: Option[Date] = None
  )
