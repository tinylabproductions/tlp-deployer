package com.tinylabproductions.uploader.reporting

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._

case class TimeData(startTime: LocalDateTime, endTime: Option[LocalDateTime]) {
  def timeTaken = ChronoUnit.SECONDS.between(
    startTime, endTime.getOrElse(LocalDateTime.now())
  ).seconds

  def elapsedStr = TimeData.elapsedStr(timeTaken)
}
object TimeData {
  def elapsedStr(total: FiniteDuration) = elapsedStrSeconds(total.toSeconds)

  def elapsedStrSeconds(totalSeconds: Long) = {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds - minutes * 60
    f"$minutes%02d:$seconds%02d"
  }
}