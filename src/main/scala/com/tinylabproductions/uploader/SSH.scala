package com.tinylabproductions.uploader

import java.nio.file.Path

import com.jcraft.jsch.agentproxy.{AgentProxy, ConnectorFactory}
import net.schmizz.sshj.SSHClient

/**
  * Created by arturas on 2016-04-17.
  */
object SSH {
  def apply(hostname: String, user: String, knownHosts: Path): SSHClient = {
    val agentProxy = new AgentProxy(ConnectorFactory.getDefault.createConnector())

    val ssh = new SSHClient()
    ssh.loadKnownHosts(knownHosts.toFile)
    ssh.connect(hostname)
    ssh.auth(user, agentProxy.authMethods: _*)
    ssh
  }
}