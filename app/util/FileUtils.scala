package util

object FileUtils {
  /**
   * Given a number return a human readable count, unlike apache.commons this will not round down. This
   * can either return as multiples of 1000 (si=true) or 1024 (si=false).
   */
  def humanReadableByteCount(bytes:Long, precision:Integer=1, si:Boolean=true): String = {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) {
      s"${bytes} B"
    } else {
      val exp = (Math.log(bytes)/ Math.log(unit)).toInt
      val pre = if (si) {
        "kMGTPE".charAt(exp-1)
      } else {
        "KMGTPE".charAt(exp-1) + "i"
      }
      val format="%%.%df".format(precision)
      "%.1f %sB".format(bytes / Math.pow(unit, exp), pre)
    }
  }
}
