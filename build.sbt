name := "TLP Deployer"

version := "1.0.1"

scalaVersion := "2.11.7"

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-simple" % "1.7.21",
  "com.iheart" %% "ficus" % "1.2.0",
  "com.hierynomus" % "sshj" % "0.15.0",
  "com.jcraft" % "jsch.agentproxy.connector-factory" % "0.0.9",
  "com.jcraft" % "jsch.agentproxy.sshj" % "0.0.9" exclude("net.schmizz", "sshj"),
  "com.jcraft" % "jsch.agentproxy.pageant" % "0.0.9",
  "com.jcraft" % "jsch.agentproxy.sshagent" % "0.0.9"
)