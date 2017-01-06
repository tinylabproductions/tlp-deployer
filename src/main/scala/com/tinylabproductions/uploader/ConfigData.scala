package com.tinylabproductions.uploader

import java.nio.file.{Path, Paths}

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.sf.sevenzipjbinding._

import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import utils._

case class ServerData(
  knownHosts: Path, hosts: Vector[String], user: String,
  deployTo: String, oldReleasesToKeep: Int,
  timeout: FiniteDuration, connectRetries: Int
)
sealed trait CompressionFormat {
  def extension: String
  def toSevenZip: ArchiveFormat
  def createArchive(): IOutCreateArchive[IOutItemAllFormats] = {
    val out = SevenZip.openOutArchive(toSevenZip)
    this.matched[CompressionFormat.WithLevel].foreach { wl =>
      out.asInstanceOf[IOutFeatureSetLevel].setLevel(wl.level.level)
    }
    this.matched[CompressionFormat.SevenZip].foreach { cf =>
      out.asInstanceOf[IOutCreateArchive7z].setSolid(cf.solid)
    }
    out.matched[IOutFeatureSetMultithreading].foreach { setting =>
      val maxThreadCount = this match {
        case _: CompressionFormat.SevenZip => 2
        case _ => Int.MaxValue
      }
      val threadCount = Runtime.getRuntime.availableProcessors().min(maxThreadCount)
      setting.setThreadCount(threadCount)
    }
    out
  }
}
object CompressionFormat {
  sealed trait WithLevel extends CompressionFormat {
    def level: CompressionLevel
  }
  sealed trait TarFirst extends WithLevel {
    def tarPath(p: Path) = p.resolveSibling(p.getFileName + ".tar")
  }

  case class Zip(level: CompressionLevel) extends WithLevel {
    override def extension = "zip"
    override def toSevenZip = ArchiveFormat.ZIP
  }
  case object Tar extends CompressionFormat {
    override def extension = "tar"
    override def toSevenZip = ArchiveFormat.TAR
  }
  case class TarGzip(level: CompressionLevel) extends TarFirst {
    override def extension = "tar.gz"
    override def toSevenZip = ArchiveFormat.GZIP
  }
  case class TarBz2(level: CompressionLevel) extends TarFirst {
    override def extension = "tar.bz2"
    override def toSevenZip = ArchiveFormat.BZIP2
  }
  case class SevenZip(level: CompressionLevel, solid: Boolean) extends WithLevel {
    override def extension = "7z"
    override def toSevenZip = ArchiveFormat.SEVEN_ZIP
  }
}

sealed abstract class CompressionLevel(val level: Int)
object CompressionLevel {
  case object Copy extends CompressionLevel(0)
  case object Fastest extends CompressionLevel(1)
  case object Fast extends CompressionLevel(3)
  case object Normal extends CompressionLevel(5)
  case object Maximum extends CompressionLevel(7)
  case object Ultra extends CompressionLevel(9)
}

case class DeployData(
  timestampFile: Option[String],
  directoryToDeploy: Path
)

case class ConfigData(
  server: ServerData, compression: CompressionFormat, deployData: DeployData
)

object HOCONReader {
  def read(cfgDir: Path, cfg: Config, directoryToDeploy: Path, ignoreTimestampFile: Boolean) = Try {
    val server = {
      def key(k: String) = s"server.$k"
      def path(key: String) = cfgDir.resolve(cfg.as[String](key))

      val releasesToKeepK = key("releases_to_keep")
      val releasesToKeep = cfg.as[Int](releasesToKeepK)
      if (releasesToKeep < 1) throw new Exception(
        s"I'm pretty sure that '$releasesToKeepK' should be at least 1 (was: $releasesToKeep)"
      )

      ServerData(
        knownHosts = path(key("known_hosts_file")),
        hosts = cfg.as[Vector[String]](key("hosts")), user = cfg.as[String](key("user")),
        deployTo = cfg.as[String](key("deploy_to")),
        oldReleasesToKeep = releasesToKeep,
        timeout = cfg.as[FiniteDuration](key("timeout")),
        connectRetries = cfg.as[Int](key("connect_retries"))
      )
    }
    val deploy = {
      def key(k: String) = s"deploy.$k"

      DeployData(
        timestampFile =
          if (ignoreTimestampFile) None
          else cfg.as[String](key("timestamp_file")).trim match {
            case "" => None
            case s => Some(s)
          },
        directoryToDeploy = directoryToDeploy
      )
    }
    val compression = {
      def key(k: String) = s"compression.$k"

      val level = {
        import CompressionLevel._
        cfg.as[String](key("level")) match {
          case "copy" => Copy
          case "fastest" => Fastest
          case "fast" => Fast
          case "normal" => Normal
          case "maximum" => Maximum
          case "ultra" => Ultra
        }
      }

      cfg.as[String](key("format")) match {
        case "zip" => CompressionFormat.Zip(level)
        case "tbz2" => CompressionFormat.TarBz2(level)
        case "tgz" => CompressionFormat.TarGzip(level)
        case "tar" => CompressionFormat.Tar
        case "7z" => CompressionFormat.SevenZip(
          level,
          cfg.as[Boolean]("7z.solid")
        )
      }
    }
    ConfigData(server, compression, deploy)
  }
}
