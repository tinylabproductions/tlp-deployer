package com.tinylabproductions.uploader.reporting

import com.tinylabproductions.uploader.ansi.Ansi


/**
  * Created by arturas on 2016-08-16.
  */
class StringReporter {
  private[this] var previousReportLines = 0

  def printReport(data: TraversableOnce[(String, String)]): Unit = {
    val msgs = data.toVector.sortBy(_._1).map { case (name, msg) =>

      val nameS = Ansi.Color.GREEN(name)
      s"[$nameS] $msg"
    }
    synchronized {
      if (previousReportLines != 0) {
        print(Ansi.cursorUp(previousReportLines))
        print(Ansi.eraseScreenDown)
      }

      msgs.foreach(println)
      previousReportLines = msgs.size
    }
  }
}
