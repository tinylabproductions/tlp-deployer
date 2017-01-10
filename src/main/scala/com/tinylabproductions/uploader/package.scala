package com.tinylabproductions

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Path
import java.time.chrono.ChronoZonedDateTime

import com.jcraft.jsch.agentproxy.AgentProxy
import com.jcraft.jsch.agentproxy.sshj.AuthAgent
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.userauth.method.AuthMethod

package object uploader {
  implicit class AgentProxyExts(val ap: AgentProxy) extends AnyVal {
    def authMethods: Array[AuthMethod] = ap.getIdentities.map(new AuthAgent(ap, _))
  }

  implicit class PathExts(val p: Path) extends AnyVal {
    def toUnixPathStr: String = p.toString.replace('\\', '/')
  }

  implicit class RemoteFileExts(val rf: RemoteFile) extends AnyVal {
    def readString(charset: Charset = StandardCharsets.UTF_8): String = {
      val arr = new Array[Byte](rf.length().toInt)
      rf.read(0, arr, 0, arr.length)
      new String(arr, charset)
    }
  }

  implicit class ComparableExts[A <: Comparable[A]](val cmp: A) extends AnyVal with Ordered[A] {
    override def compare(that: A) = cmp.compareTo(that)
  }

  implicit class ChronoZonedDateTimeExts(val thiz: ChronoZonedDateTime[_])
  extends AnyVal with Ordered[ChronoZonedDateTime[_]]
  {
    override def compare(that: ChronoZonedDateTime[_]) = thiz.compareTo(that)
  }
}
