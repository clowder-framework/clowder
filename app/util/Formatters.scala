package util

/**
 * Created by lmarini on 4/21/14.
 */
object Formatters {

  val ellipsis = "..."

  def ellipsize(s: String, max: Int): String = {
    if (s.length <= max) return s
    s.substring(0, max - ellipsis.length) + ellipsis
  }

}
