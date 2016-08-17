package com.tinylabproductions.uploader.reporting

import com.github.tomaslanger.chalk.{Ansi, Chalk}

/**
  * Created by arturas on 2016-08-16.
  */
class StringReporter {
  private[this] var previousReportLines = 0

  def printReport(data: TraversableOnce[(String, String)]): Unit = {
    val msgs = data.toVector.sortBy(_._1).map { case (name, msg) =>
      val nameS = Chalk.on(name).bold()
      s"[$nameS] $msg"
    }
    synchronized {
      print(Ansi.cursorUp(previousReportLines))
      print(Ansi.eraseScreenDown())

      msgs.foreach(println)
      previousReportLines = msgs.size
    }
  }
}
