package com.tinylabproductions.uploader

/**
  * Created by arturas on 2016-07-08.
  */
package object utils {
  implicit class LongExts(val l: Long) extends AnyVal {
    def asHumanReadable(si: Boolean) = {
      val unit = if (si) 1000 else 1024
      if (l < unit) s"$l B"
      else {
        val exp = (math.log(l) / math.log(unit)).toInt
        val pre = (if (si) "kMGTPE" else "KMGTPE").charAt(exp - 1) + (if (si) "" else "i")
        f"${l / math.pow(unit, exp)}%.1f ${pre}B"
      }
    }

    def asHumanReadableSize = l.asHumanReadable(si = false)
  }
}
