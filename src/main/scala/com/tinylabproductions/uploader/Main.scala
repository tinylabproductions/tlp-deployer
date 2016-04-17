package com.tinylabproductions.uploader

import java.io.File
import java.nio.file.Paths

import com.typesafe.config.ConfigFactory

import scala.util.Try

object Main {
  def main(args: Array[String]) = {
    args match {
      case Array(configFileS, directoryToDeployS) =>
        Try {
          val cfg = ConfigFactory.parseFile(new File(configFileS))
          val toDeploy = Paths.get(directoryToDeployS)
          if (!toDeploy.toFile.isDirectory)
            throw new RuntimeException(s"$toDeploy needs to be a directory!")
          HOCONReader.read(cfg, toDeploy)
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

  def selfName = new File(getClass.getProtectionDomain.getCodeSource.getLocation.toURI.getPath).getName

  def printHelp() = {
    println(s"Usage: java -jar $selfName path_to_config.conf directory_to_deploy")
  }
}