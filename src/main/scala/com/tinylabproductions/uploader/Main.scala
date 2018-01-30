package com.tinylabproductions.uploader

import java.security.Security

import com.typesafe.config.ConfigFactory
import org.fusesource.jansi.AnsiConsole
import com.softwaremill.quicklens._
import org.bouncycastle.jce.provider.BouncyCastleProvider

import scala.util.Try

object Main {
  def main(args: Array[String]): Unit = {
    AnsiConsole.systemInstall()
    Security.addProvider(new BouncyCastleProvider())

    CLIArgs.parser.parse(args, CLIArgs()) match {
      case Some(cli) =>
        Try {
          val cfg = ConfigFactory.parseFile(cli.config.toFile)
          val toDeploy = cli.directoryToDeploy
          if (!toDeploy.toFile.isDirectory)
            throw new RuntimeException(s"$toDeploy needs to be a directory!")
          // need to call toRealPath, because otherwise it returns null parent on 'foo.conf' path.
          val cfgDir = cli.config.toRealPath().getParent
          HOCONReader.read(
            cfgDir, cfg, toDeploy,
            ignoreTimestampFile = cli.ignoreTimestamp
          )
        }.flatten.map { cfg =>
          val hosts = cli.overrideHosts
          cfg
            .modify(_.server.hosts).setToIf(hosts.nonEmpty)(hosts)
            .modify(_.deployData.deploy).setToIf(cli.skipDeploy)(None)
            .modify(_.deployData.postDeploy).setToIf(cli.skipPostDeploy)(None)
        } match {
          case util.Success(cfg) =>
            Deployer.deploy(cfg)
          case util.Failure(err) =>
            throw err
        }
      case None =>
        System.exit(1)
    }
  }
}