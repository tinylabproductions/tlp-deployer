package com.tinylabproductions.uploader

import java.io.File
import java.nio.file.Paths

import com.typesafe.config.ConfigFactory
import org.fusesource.jansi.AnsiConsole

import scala.util.Try

object Main {
  object Opts {
    val IgnoreTimestamp = "--ignore-timestamp"
  }

  def main(args: Array[String]): Unit = {
    AnsiConsole.systemInstall()

    args match {
      case Array(configFileS, directoryToDeployS, options @ _*) =>
        Try {
          val cfgPath = Paths.get(configFileS)
          val cfg = ConfigFactory.parseFile(cfgPath.toFile)
          val toDeploy = Paths.get(directoryToDeployS)
          if (!toDeploy.toFile.isDirectory)
            throw new RuntimeException(s"$toDeploy needs to be a directory!")
          HOCONReader.read(
            cfgPath.getParent, cfg, toDeploy,
            ignoreTimestampFile = options.contains(Opts.IgnoreTimestamp)
          )
        }.flatten match {
          case util.Success(cfg) =>
            Deployer.deploy(cfg)
          case util.Failure(err) =>
            throw err
        }
      case _ =>
        printHelp()
        System.exit(2)
    }
  }

  def printHelp(): Unit = {
    println(s"Usage: tlp-deployer path_to_config.conf directory_to_deploy [options]")
    println()
    println(s"Options:")
    println(s"  ${Opts.IgnoreTimestamp}")
    println(s"     Deploys even if timestamp says you are deploying an older version.")
  }
}