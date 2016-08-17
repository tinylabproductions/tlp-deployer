package com.tinylabproductions.uploader.reporting

import java.time.LocalDateTime
import java.util.{Timer, TimerTask}
import com.softwaremill.quicklens._

/**
  * Created by arturas on 2016-08-16.
  */
class SyncOpReporter(hosts: TraversableOnce[String]) extends StringReporter {
  private[this] var datum = hosts.map((_, TimeData(LocalDateTime.now(), None))).toMap

  def printReport(): Unit = printReport(datum.map { case (host, timeData) =>
    val stateStr = if (timeData.endTime.isDefined) "done" else "working"
    (host, s"$stateStr\t${timeData.elapsedStr}")
  })

  def allCompleted = datum.forall(_._2.endTime.isDefined)

  def hostCompleted(host: String) = {
    datum += host -> datum(host).modify(_.endTime).setTo(Some(LocalDateTime.now()))
    printReport()
  }

  SyncOpReporter.timer.schedule(new TimerTask {
    override def run() = {
      printReport()
      if (allCompleted) cancel()
    }
  }, 0, 1000)
}
object SyncOpReporter {
  private val timer = new Timer("SyncOpReporter", true)
}
