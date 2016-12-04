name := "TLP Deployer"

version := "1.4"

scalaVersion := "2.11.7"

resolvers += Resolver.jcenterRepo

val JschAgentProxyVer = "0.0.9"

val BouncyCastleVer = "1.54"

val SevenZipVer = "9.20-2.00beta"

libraryDependencies ++= Seq(
  // Logging
  "org.slf4j" % "slf4j-simple" % "1.7.21",

  // Scala-friendly companion to Typesafe config
  "com.iheart" %% "ficus" % "1.2.0",

  // SSH and friends
  "com.hierynomus" % "sshj" % "0.15.0",
  "com.jcraft" % "jsch.agentproxy.connector-factory" % JschAgentProxyVer,
  "com.jcraft" % "jsch.agentproxy.sshj" % JschAgentProxyVer exclude("net.schmizz", "sshj"),
  "com.jcraft" % "jsch.agentproxy.pageant" % JschAgentProxyVer,
  "com.jcraft" % "jsch.agentproxy.sshagent" % JschAgentProxyVer,

  // Terminal handling
  "com.github.tomas-langer" % "chalk" % "1.0.2",

  // Cryptography libraries
  "org.bouncycastle" % "bcprov-jdk15on" % BouncyCastleVer,
  "org.bouncycastle" % "bcpkix-jdk15on" % BouncyCastleVer,

  // 7zip
  "net.sf.sevenzipjbinding" % "sevenzipjbinding" % SevenZipVer,
  "net.sf.sevenzipjbinding" % "sevenzipjbinding-all-platforms" % SevenZipVer,

  // Functional programming
  "com.softwaremill.quicklens" %% "quicklens" % "1.4.7"
)
