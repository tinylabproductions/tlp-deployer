package com.tinylabproductions.uploader

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import com.github.tomaslanger.chalk.{Ansi, Chalk}
import net.schmizz.sshj.common.StreamCopier.Listener
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.xfer.TransferListener
import com.tinylabproductions.uploader.utils._

class SingleFileProgressReporter {
  private[this] case class Data(
    startTime: LocalDateTime, endTime: Option[LocalDateTime], transferred: Long, size: Long
  ) {
    def completed = transferred.toDouble / size
    def remaining = size - transferred
    def isDone = transferred == size
  }

  private[this] var datum = Map.empty[String, Data]
  private[this] var previousReportLines = 0

  private[this] def elapsedStr(totalSeconds: Long) = {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds - minutes * 60
    f"$minutes%02d:$seconds%02d"
  }

  def printReport(): Unit = synchronized {
    val msg = datum.toVector.sortBy(_._1).map { case (name, data) =>
      val endTime = data.endTime.getOrElse(LocalDateTime.now())
      val totalS = ChronoUnit.SECONDS.between(data.startTime, endTime)
      val speed = if (totalS > 0) data.transferred / totalS else 0
      val remainingEtaTime = if (speed == 0) None else Some(data.remaining / speed)

      val nameS = Chalk.on(name).bold()
      val transferredPercentage = if (data.isDone) "done" else f"${data.completed * 100}%2.1f%%"
      val transferred =
        f"${data.transferred.asHumanReadableSize}/${data.size.asHumanReadableSize
        }\t($transferredPercentage)"
      val elapsed = elapsedStr(totalS)
      val remaining = s"ETA ${remainingEtaTime.fold("--:--")(elapsedStr)}"
      s"[$nameS] $transferred\t${speed.asHumanReadableSize}/s\t$elapsed\t$remaining"
    }
    print(Ansi.cursorUp(previousReportLines))
    print(Ansi.eraseScreenDown())
    msg.foreach(println)
    previousReportLines = msg.size
  }

  private[this] def update(name: String, data: Data) = {
    datum += name -> data
    printReport()
  }

  def reportFTP[A](opName: String, client: SFTPClient)(f: => A) = {
    val transfer = client.getFileTransfer
    val oldListener = transfer.getTransferListener

    transfer.setTransferListener(new TransferListener {
      override def directory(name: String) = this
      override def file(name: String, size: Long) = {
        val data = Data(LocalDateTime.now(), None, 0, size)
        update(opName, data)

        new Listener {
          override def reportProgress(transferred: Long) = {
            update(opName, {
              var newData = data.copy(transferred = transferred)
              if (transferred == size) newData = newData.copy(endTime = Some(LocalDateTime.now()))
              newData
            })
          }
        }
      }
    })

    val ret = f
    transfer.setTransferListener(oldListener)
    ret
  }
}
