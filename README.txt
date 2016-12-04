Simple deployer that works over SSH public key authentication (supports pageant).

It was written in mind with deployment to Linux servers in mind.

It creates the following structure under deploy_dir:
  deploy_dir\
  -- 2016-04-17T18_45_29.994
  -- 2016-04-17T19_03_11.122
  # Latest release
  -- current -> 2016-04-17T19_03_11.122

It also cleans up old releases.

Use sbt (http://www.scala-sbt.org/) to create an executable jar:

> sbt assembly

Then run it with:

> java -jar your_jar.jar
