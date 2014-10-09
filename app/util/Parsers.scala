package util

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
  def parseDouble(s: JsValue) : Option[Double] = parseDouble(s.toString())

  /**
   * Given the string return a double. This will ignore any starting and ending quotes.
   *
   * @param s the string to parse
   * @return returns Some(double) if it worked, or None if not.
   */
  def parseDouble(s: String) : Option[Double] = try { Some(parseString(s).toDouble) } catch { case _ : NumberFormatException => None }

  /**
   * Given the JsValue, strip off any quotes from beginning and end.
   *
   * @param s the JsValue to be cleaned
   * @return the cleaned up JsValue as string.
   */
  def parseString(s: JsValue) : String = parseString(s.toString())

  /**
   * Given the string, strip off any quotes from beginning and end.
   *
   * @param s the string to be cleaned
   * @return the cleaned up string.
   */
  def parseString(s: String) : String = s.replaceAll("^[\"'](.*)[\"']", "$1")
}
