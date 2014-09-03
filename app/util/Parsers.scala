package util

/**
 * Created by kooper on 9/2/14.
 */
object Parsers {
  def parseDouble(s: String) = try { Some(s.replace("\"", "").toDouble) } catch { case _ : NumberFormatException => None }
}
