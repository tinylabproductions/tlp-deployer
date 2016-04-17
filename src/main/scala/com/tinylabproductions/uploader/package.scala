package com.tinylabproductions

import java.nio.file.Path

import com.jcraft.jsch.agentproxy.AgentProxy
import com.jcraft.jsch.agentproxy.sshj.AuthAgent
import net.schmizz.sshj.userauth.method.AuthMethod

package object uploader {
  implicit class AgentProxyExts(val ap: AgentProxy) extends AnyVal {
    def authMethods: Array[AuthMethod] = ap.getIdentities.map(new AuthAgent(ap, _))
  }

  implicit class PathExts(val p: Path) extends AnyVal {
    def toUnixPathStr = p.toString.replace('\\', '/')
  }
}
