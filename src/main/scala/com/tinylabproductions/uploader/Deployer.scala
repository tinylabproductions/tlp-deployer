package com.tinylabproductions.uploader

import java.nio.file.{Files, Paths}
import java.time.{LocalDateTime, ZoneId}
import java.util.concurrent.TimeUnit

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.{RemoteResourceInfo, RemoteResourceFilter, FileMode, SFTPClient}
import utils.ZipUtils

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.Try

/**
  * Created by arturas on 2016-04-17.
  */
object Deployer {
  case class Client(ssh: SSHClient, ftp: SFTPClient, host: String, timeout: FiniteDuration) {
    def cmd(s: String) = {
      val cmd = ssh.startSession().exec(s)
      cmd.join(timeout.toMillis, TimeUnit.MILLISECONDS)
      if (cmd.getExitStatus != 0)
        err(s"Error executing '$s' (status ${cmd.getExitStatus}): $cmd")
      cmd
    }

    def err(s: String) = throw new Exception(s"on '$host': $s")
  }

  def deploy(cfg: ConfigData) = {
    val now = LocalDateTime.now(ZoneId.of("UTC"))

    val zipName = s"${cfg.directoryToDeploy.getFileName}_${now.toString.replace(':', '_')}.zip"
    val zip = Paths.get(zipName)
    time(s"Compressing '${cfg.directoryToDeploy}' to '$zip'") {
      ZipUtils.pack(cfg.directoryToDeploy, zip)
    }

    try {
      val clients = time("Connecting") {
        cfg.server.hosts.par.map { host =>
          val ssh = SSH(host, cfg.server.user, cfg.server.knownHosts)
          Client(ssh, ssh.newSFTPClient(), host, cfg.server.timeout)
        }
      }

      time(s"Checking if '${cfg.server.deployTo}' exists") {
        clients.foreach(ensureDir(cfg.server.deployTo))
      }

      val deploy = s"${cfg.server.deployTo}/$now"
      time(s"Creating '$deploy'") {
        clients.foreach(ensureDir(deploy))
      }
      val deployZip = s"$deploy/$zipName"
      time(s"Uploading '$zip' to '$deployZip'") {
        clients.foreach(_.ftp.put(zip.toString, deployZip))
      }

      time(s"Extracting '$deployZip'") {
        clients.foreach(_.cmd(s"unzip '$deployZip' -d '$deploy' && rm '$deployZip'"))
      }

      val currentLink = s"${cfg.server.deployTo}/current"
      time(s"Linking '$currentLink' to '$deploy'") {
        clients.foreach(_.cmd(s"ln -sfT '$deploy' '$currentLink'"))
      }

      time(s"Cleaning up old deploys (keeping ${cfg.server.oldReleasesToKeep} releases)") {
        clients.foreach { c =>
          val toDelete = c.ftp.ls(cfg.server.deployTo, new RemoteResourceFilter {
            override def accept(resource: RemoteResourceInfo) = resource.isDirectory
          }).asScala.
            flatMap(r => Try((r, LocalDateTime.parse(r.getName))).toOption).
            sortWith((a, b) => a._2.isBefore(b._2)).
            map(_._1).
            dropRight(cfg.server.oldReleasesToKeep)

          if (toDelete.nonEmpty) {
            val rmArgs = toDelete.map(_.getName).mkString("'", "' '", "'")
            /* cd to a directory first to prevent accidental deletion of / in case something goes wrong
             * http://serverfault.com/questions/587102/monday-morning-mistake-sudo-rm-rf-no-preserve-root */
            val toDeleteS = s"cd '${cfg.server.deployTo}' && rm -rf $rmArgs"
            c.cmd(toDeleteS)
          }
        }
      }
    }
    finally {
      Files.delete(zip)
    }
  }

  private def time[A](msg: String)(f: => A): A = {
    print(s"$msg... ")
    System.out.flush()
    val start = System.currentTimeMillis()
    val ret = f
    val time = System.currentTimeMillis() - start
    println(s"[${time / 1000f}s]")
    ret
  }

  private def ensureDir(path: String)(c: Client) = {
    def check(onFailure: => Unit) =
      Option(c.ftp.statExistence(path)) match {
        case None =>
          onFailure
        case Some(stat) =>
          if (stat.getType != FileMode.Type.DIRECTORY)
            c.err(s"'$path' is not a directory: $stat")
      }

    check {
      c.ftp.mkdirs(path)
      check {
        c.err(s"Can't make a directory at '$path'")
      }
    }
  }
}
