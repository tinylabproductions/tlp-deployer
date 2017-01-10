package com.tinylabproductions.uploader

import java.nio.file.Path

import com.softwaremill.quicklens._
import scopt.{OptionDef, Read}

import scala.language.implicitConversions

/**
  * Created by arturas on 2017-01-06.
  */
case class CLIArgs(
  config: Path = null,
  directoryToDeploy: Path = null,
  ignoreTimestamp: Boolean = false,
  skipPostDeploy: Boolean = false,
  overrideHosts: Vector[String] = Vector.empty
)
object CLIArgs {
  implicit val pathRead: Read[Path] = Read.fileRead.map(_.toPath)
  implicit def vectorRead[A : Read]: Read[Vector[A]] = Read.seqRead[A].map(_.toVector)

  val parser = new scopt.OptionParser[CLIArgs]("tlp-deployer") {
    opt[Path]('c', "config")
      .required()
      .text("path to configuration file")
      .action2(_.modify(_.config))

    opt[Path]('d', "dir")
      .required()
      .text("directory to deploy")
      .action2(_.modify(_.directoryToDeploy))

    opt[Vector[String]]('h', "hosts")
      .text("override hosts setting in configuration to given hosts")
      .valueName("host1,host2")
      .action2(_.modify(_.overrideHosts))

    opt[Unit]("skip-post-deploy")
      .text("does not run post deploy commands")
      .action((_, a) => a.copy(skipPostDeploy = true))

    opt[Unit]("ignore-timestamp")
      .text("deploys even if timestamp says you are deploying an older version")
      .action((_, a) => a.copy(ignoreTimestamp = true))
  }

  implicit class OptionDefExts[Value, Args](val odef: OptionDef[Value, Args]) {
    def action2(f: Args => PathModify[Args, Value]): OptionDef[Value, Args] =
      odef.action((value, args) => f(args).setTo(value))
  }
}
