package com.tinylabproductions.uploader

import java.nio.file.{Files, Paths}
import java.time.{LocalDateTime, ZoneId}
import java.util.concurrent.TimeUnit

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.{FileMode, RemoteResourceFilter, RemoteResourceInfo, SFTPClient}
import utils.ZipUtils

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.collection.parallel.immutable.ParVector
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

    val clients = timed("Connecting") {
      cfg.server.hosts.par.map { host =>
        val clientOpt = withRetries(s"connection to $host", cfg.server.connectRetries) {
          val ssh = SSH(host, cfg.server.user, cfg.server.knownHosts)
          Client(ssh, ssh.newSFTPClient(), host, cfg.server.timeout)
        }
        clientOpt.getOrElse {
          throw new Exception(
            s"Can't connect to $host in ${cfg.server.connectRetries} retries."
          )
        }
      }
    }

    timed(s"Compressing '${cfg.directoryToDeploy}' to '$zip'") {
      ZipUtils.pack(cfg.directoryToDeploy, zip)
    }

    try {
      timed(clients, s"Checking if '${cfg.server.deployTo}' exists") {
        ensureDir(cfg.server.deployTo)
      }

      val deploy = s"${cfg.server.deployTo}/$now"
      timed(clients, s"Creating '$deploy'")(ensureDir(deploy))

      val deployZip = s"$deploy/$zipName"
      timed(clients, s"Uploading '$zip' (${zip.toFile.length()}) to '$deployZip'") {
        _.ftp.put(zip.toString, deployZip)
      }

      timed(clients, s"Extracting '$deployZip'") {
        _.cmd(s"unzip '$deployZip' -d '$deploy' && rm '$deployZip'")
      }

      val currentLink = s"${cfg.server.deployTo}/current"
      timed(clients, s"Linking '$currentLink' to '$deploy'") {
        _.cmd(s"ln -sfT '$deploy' '$currentLink'")
      }

      timed(
        clients, s"Cleaning up old deploys (keeping ${cfg.server.oldReleasesToKeep} releases)"
      ) { c =>
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
    finally {
      Files.delete(zip)
    }
  }

  private def withRetries[A](message: String, retries: Int)(f: => A): Option[A] = {
    (0 to retries).foreach { tryIdx =>
      try {
        return Some(f)
      }
      catch {
        case ex: Exception =>
          val actionMsg = if (tryIdx + 1 == retries) "giving up" else "retrying"
          println(s"Try ${tryIdx + 1} of $message failed with $ex, $actionMsg.")
      }
    }
    None
  }

  private def timed[A](msg: String)(f: => A): A = {
    print(s"$msg... ")
    System.out.flush()
    val start = System.currentTimeMillis()
    val ret = f
    val time = System.currentTimeMillis() - start
    println(s"[${time / 1000f}s]")
    ret
  }

  private def timed[A](clients: ParVector[Client], msg: String)(f: Client => A): ParVector[A] = {
    println(s"$msg starting for ${clients.size} hosts")
    val totalStart = System.currentTimeMillis()
    val ret = clients.map { client =>
      val start = System.currentTimeMillis()
      val retIn = f(client)
      val time = System.currentTimeMillis() - start
      printlnsync(s"[${client.host}] $msg completed in ${time / 1000f}s")
      retIn
    }
    val totalTime = System.currentTimeMillis() - totalStart
    println(s"$msg for ${clients.size} hosts took ${totalTime / 1000f}s")
    ret
  }

  private[this] def printlnsync(s: String) = synchronized { println(s) }

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
