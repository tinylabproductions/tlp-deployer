package com.tinylabproductions.uploader

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.time._
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import com.tinylabproductions.uploader.reporting.{SingleFileProgressReporter, SyncOpReporter}
import com.tinylabproductions.uploader.utils._
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.connection.channel.direct.Session.Command
import net.schmizz.sshj.sftp.{FileMode, RemoteResourceFilter, RemoteResourceInfo, SFTPClient}
import net.schmizz.sshj.transport.TransportException

import scala.collection.JavaConverters._
import scala.collection.parallel.immutable.ParVector
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

/**
  * Created by arturas on 2016-04-17.
  */
object Deployer {
  val ReleaseDirFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh_mm_ss.SSS")

  case class Client(ssh: SSHClient, ftp: SFTPClient, host: String, timeout: FiniteDuration) {
    def cmd(s: String): Command = {
      val cmd = ssh.startSession().exec(s)
      cmd.join(timeout.toMillis, TimeUnit.MILLISECONDS)
      if (cmd.getExitStatus != 0)
        err(s"Error executing '$s' (status ${cmd.getExitStatus}): $cmd")
      cmd
    }

    def err(s: String) = throw new Exception(s"on '$host': $s")
  }

  def deploy(cfg: ConfigData): Unit = {
    {
      val dd = cfg.deployData
      val missing = dd.requiredFiles.collect {
        case path if Files.notExists(dd.directoryToDeploy.resolve(path)) =>
          path
      }
      if (missing.nonEmpty) throw new Exception(
        s"There are missing required files in ${dd.directoryToDeploy}: ${missing.mkStringEnum()}"
      )
    }

    val now = LocalDateTime.now(ZoneId.of("UTC"))

    val directoryToDeploy = cfg.deployData.directoryToDeploy
    val currentLink = s"${cfg.server.deployTo}/current"

    def parseTimestampFile(data: String, filename: String) = {
      try {
        OffsetDateTime.parse(data.trim, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
          .atZoneSameInstant(ZoneOffset.UTC)
      }
      catch {
        case e: Exception =>
          throw new Exception(
            s"Timestamp file '$filename' must contain a date in " +
            s"DateTimeFormatter.ISO_OFFSET_DATE_TIME format!"
          )
      }
    }

    case class LocalTimestampData(local: ZonedDateTime, remoteTsf: String)
    case class RemoteTimestampData(latestDeployment: ZonedDateTime, clients: Vector[Client])
    case class TimestampData(local: ZonedDateTime, remote: Option[RemoteTimestampData])

    val localTimestampData =
      cfg.deployData.timestampFile.map { tsf =>
        val localTsf = directoryToDeploy.resolve(tsf)
        val remoteTsf = s"$currentLink/$tsf"
        if (! localTsf.toFile.exists())
          throw new Exception(
            s"Can't read local timestamp file '$localTsf'! Please check your configuration."
          )
        val str = new String(Files.readAllBytes(localTsf), StandardCharsets.UTF_8)
        val local = parseTimestampFile(str, tsf.toString)
        LocalTimestampData(local, remoteTsf)
      }

    val zipName =
      s"${directoryToDeploy.getFileName}_${now.toString.replace(':', '_')}.${
      cfg.compression.extension}"
    val zip = Paths.get(System.getProperty("java.io.tmpdir"), zipName)

    val zipFuture = Future {
      val (_, time) = timeAction {
        SevenZipUtils.pack(directoryToDeploy, zip, cfg.compression)
      }
      time
    }

    println(s"Using known hosts file: ${cfg.server.knownHosts}")
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

    val timestampData = localTimestampData.map { localTSD =>
      val results = timed(
        clients,
        s"Collecting remote deployment timestamps from '${localTSD.remoteTsf}'"
      ) { client =>
        val remoteTSF = localTSD.remoteTsf.toString
        Option(client.ftp.statExistence(remoteTSF)).map { _ =>
          val data = client.ftp.getSFTPEngine.open(remoteTSF).readString()
          (parseTimestampFile(data, localTSD.remoteTsf), client)
        }
      }
      val remote = results.foldLeft(Option.empty[RemoteTimestampData]) {
        case (current @ Some(r), Some((b, bClient))) =>
          if (b > r.latestDeployment)
            Some(RemoteTimestampData(b, Vector(bClient)))
          else if (b == r.latestDeployment)
            Some(r.copy(clients = r.clients :+ bClient))
          else
            current
        case (a, None) => a
        case (None, Some((b, bClient))) => Some(RemoteTimestampData(b, Vector(bClient)))
      }
      TimestampData(localTSD.local, remote)
    }

    timestampData.foreach { tsd =>
      tsd.remote.foreach { remote =>
        val isNewer = tsd.local > remote.latestDeployment
        if (!isNewer) throw new Exception(
          s"Aborting upload of older package!\n" +
          s"Local: ${tsd.local}\n" +
          s"Remote: ${remote.latestDeployment} (on hosts: ${
            remote.clients.map(_.host).mkString(", ")})"
        )
      }
    }

    println(
      s"Waiting for compression to finish (${cfg.compression}) ('${
      directoryToDeploy}' -> '$zip'..."
    )
    val zipTime = Await.result(zipFuture, 5.minutes)
    println(f"Compression took ${zipTime / 1000f}%.2fs")

    try {
      timed(clients, s"Checking if '${cfg.server.deployTo}' exists") {
        ensureDir(cfg.server.deployTo)
      }

      val deploy = s"${cfg.server.deployTo}/${now.format(ReleaseDirFormatter)}"
      timed(clients, s"Creating '$deploy'")(ensureDir(deploy))

      val deployZip = s"$deploy/$zipName"
      val reporter = new SingleFileProgressReporter
      timed(s"Uploading '$zip' (${zip.toFile.length().asHumanReadableSize}) to '$deployZip'") {
        println() // We need the newline.
        clients.foreach { client =>
          reporter.reportFTP(client.host, client.ftp) {
            client.ftp.put(zip.toString, deployZip)
          }
        }
      }

      timed(clients, s"Extracting '$deployZip'") {
        _.cmd((cfg.compression match {
          case _: CompressionFormat.Zip => s"unzip '$deployZip' -d '$deploy'"
          case CompressionFormat.Tar => s"tar -xf '$deployZip' -C '$deploy'"
          case _: CompressionFormat.TarBz2 => s"tar -xjf '$deployZip' -C '$deploy'"
          case _: CompressionFormat.TarGzip => s"tar -xzf '$deployZip' -C '$deploy'"
          case _: CompressionFormat.SevenZip =>
            // x = eXtract
            // -bd: Disable percentage indicator
            s"7zr x -bd -o'$deploy' '$deployZip'"
        }) + s" > '$deployZip.log' && rm '$deployZip'")
      }

      timed(clients, s"Linking '$currentLink' to '$deploy'") {
        _.cmd(s"ln -sfT '$deploy' '$currentLink'")
      }

      timed(
        clients, s"Cleaning up old deploys (keeping ${cfg.server.oldReleasesToKeep} releases)"
      ) { c =>
        val toDelete = c.ftp.ls(cfg.server.deployTo, new RemoteResourceFilter {
          override def accept(resource: RemoteResourceInfo) = resource.isDirectory
        }).asScala.
          flatMap { r =>
            def doTry(dateTimeFormatter: DateTimeFormatter) =
              Try((r, LocalDateTime.parse(r.getName, dateTimeFormatter))).toOption
            // Support new and old styles.
            doTry(ReleaseDirFormatter) orElse doTry(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
          }.
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

      cfg.deployData.postDeploy.foreach { cmd =>
        timed(clients, s"Running: '$cmd'")(_.cmd(cmd))
      }
    }
    finally {
      Files.delete(zip)
    }
  }

  val RetryableReasons = Set(
    DisconnectReason.CONNECTION_LOST,
    DisconnectReason.SERVICE_NOT_AVAILABLE,
    DisconnectReason.TOO_MANY_CONNECTIONS,
    DisconnectReason.UNKNOWN
  )
  private def withRetries[A](message: String, retries: Int)(f: => A): Option[A] = {
    (0 to retries).foreach { tryIdx =>
      try {
        return Some(f)
      }
      catch {
        case ex: TransportException if RetryableReasons.contains(ex.getDisconnectReason) =>
          val actionMsg = if (tryIdx + 1 == retries) "giving up" else "retrying"
          println(s"Try ${tryIdx + 1} of $message failed with $ex, $actionMsg.")
        case ex: TransportException
          if ex.getDisconnectReason == DisconnectReason.HOST_KEY_NOT_VERIFIABLE
        =>
          Console.err.println(
            """##################################################################################
              |###
              |### Can't verify host key! Please add host key
              |### (you can use `ssh-keyscan -H your.host.name >> known_hosts_file`) to your
              |### known hosts file specified in deployer configuration!
              |###
              |##################################################################################
            """.stripMargin)
          throw ex
      }
    }
    None
  }

  private def timeAction[A](f: => A): (A, Long) = {
    val start = System.currentTimeMillis()
    val ret = f
    val time = System.currentTimeMillis() - start
    (ret, time)
  }

  private def timed[A](msg: String)(f: => A): A = {
    print(s"$msg... ")
    System.out.flush()
    val (ret, time) = timeAction(f)
    println(s"[${time / 1000f}s]")
    ret
  }

  private def timed[A](clients: ParVector[Client], msg: String)(f: Client => A): ParVector[A] = {
    println(s"$msg starting for ${clients.size} hosts")
    val reporter = new SyncOpReporter(clients.map(_.host).seq)
    val totalStart = System.currentTimeMillis()
    val ret = clients.map { client =>
      val retIn = f(client)
      reporter.hostCompleted(client.host)
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
