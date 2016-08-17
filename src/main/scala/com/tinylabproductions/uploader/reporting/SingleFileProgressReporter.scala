package com.tinylabproductions.uploader.reporting

import java.time.LocalDateTime

import com.github.tomaslanger.chalk.Chalk
import com.tinylabproductions.uploader.utils._
import net.schmizz.sshj.common.StreamCopier.Listener
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.xfer.TransferListener
import com.softwaremill.quicklens._

class SingleFileProgressReporter extends StringReporter {
  private[this] case class Data(
    time: TimeData, transferred: Long, size: Long
  ) {
    def completed = transferred.toDouble / size
    def remaining = size - transferred
    def isDone = transferred == size
  }

  private[this] var datum = Map.empty[String, Data]

  def printReport(): Unit = {
    val msgs = datum.map { case (name, data) =>
      val total = data.time.timeTaken
      val totalS = total.toSeconds
      val speed = if (totalS > 0) data.transferred / totalS else 0
      val remainingEtaTime = if (speed == 0) None else Some(data.remaining / speed)

      val transferredPercentage = if (data.isDone) "done" else f"${data.completed * 100}%2.1f%%"
      val transferred =
        f"${data.transferred.asHumanReadableSize}/${data.size.asHumanReadableSize
        }\t($transferredPercentage)"
      val elapsed = data.time.elapsedStr
      val remaining = s"ETA ${remainingEtaTime.fold("--:--")(TimeData.elapsedStrSeconds)}"
      (name, s"$transferred\t${speed.asHumanReadableSize}/s\t$elapsed\t$remaining")
    }
    printReport(msgs)
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
        val data = Data(TimeData(LocalDateTime.now(), None), 0, size)
        update(opName, data)

        new Listener {
          override def reportProgress(transferred: Long) = {
            update(opName, {
              var newData = data.copy(transferred = transferred)
              if (transferred == size)
                newData = newData.modify(_.time.endTime).setTo(Some(LocalDateTime.now()))
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
