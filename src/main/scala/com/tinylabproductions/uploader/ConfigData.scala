package com.tinylabproductions.uploader

import java.nio.file.{Paths, Path}

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

case class ServerData(
  knownHosts: Path, hosts: Vector[String], user: String,
  deployTo: String, oldReleasesToKeep: Int,
  timeout: FiniteDuration, connectRetries: Int
)
case class ConfigData(server: ServerData, directoryToDeploy: Path)

object HOCONReader {
  def read(cfg: Config, directoryToDeploy: Path) = Try {
    val server = {
      def key(k: String) = s"server.$k"
      def path(key: String) = Paths.get(cfg.as[String](key))

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
    ConfigData(server, directoryToDeploy)
  }
}
