package util

import java.text.SimpleDateFormat
import java.util.Date

import org.joda.time.DateTime
import play.api.libs.json.JsValue

/**
 * Created by kooper on 9/2/14.
 */
object Parsers {
  /**
   * Given the JsValue return a double. This will ignore any starting and ending quotes.
   *
   * @param s the value to parse
   * @return returns Some(double) if it worked, or None if not.
   */
  def parseDouble(s: JsValue) : Option[Double] = parseDouble(s.toString)

  /**
   * Given the string return a double. This will ignore any starting and ending quotes.
   *
   * @param s the string to parse
   * @return returns Some(double) if it worked, or None if not.
   */
  def parseDouble(s: String) : Option[Double] = try { Some(parseString(s).toDouble) } catch { case _ : NumberFormatException => None }

  /**
   * Given the JsValue return a DateTime. This will ignore any starting and ending quotes.
   *
   * @param s the value to parse
   * @return returns Some(DateTime) if it worked, or None if not.
   */
  def parseDate(s: JsValue) : Option[DateTime] = parseDate(s.toString)

  /**
   * Given the string return a DateTime. This will ignore any starting and ending quotes.
   *
   * @param s the string to parse
   * @return returns Some(DateTime) if it worked, or None if not.
   */
  def parseDate(s: String) : Option[DateTime] = try { Some(DateTime.parse(parseString(s).replace(" ", "T"))) } catch { case x: Exception => None }

  /**
   * Given the JsValue, strip off any quotes from beginning and end.
   *
   * @param s the JsValue to be cleaned
   * @return the cleaned up JsValue as string.
   */
  def parseString(s: JsValue) : String = parseString(s.toString)

  /**
   * Given the string, strip off any quotes from beginning and end.
   *
   * @param s the string to be cleaned
   * @return the cleaned up string.
   */
  def parseString(s: String) : String = s.replaceAll("^[\"'](.*)[\"']", "$1")

  /**
   * Given the JsValues, strip off any quotes from beginning and end.
   *
   * @param j1 the JsValue to be cleaned
   * @param j2 the JsValue to be cleaned
   * @return the cleaned up string.
   */
  def parseJsValues(j1: JsValue, j2: JsValue) : (String, String) = {
    (parseString(j1), parseString(j2))
  }

  /**
   * Parse the string assuming ISO8601 dateformat and return a date object.
   */
  def fromISO8601(s: String) : Date = {
    val ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    ISO8601.parse(s)
  }

  /**
   * Format a date object as a ISO8601 formatted string.
   */
  def toISO8601(d: Date) : String = {
    val ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    ISO8601.format(d)
  }
}
