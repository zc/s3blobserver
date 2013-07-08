name := "s3blobserver"

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies += "io.spray" % "spray-caching" % "1.2-M8"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.0-RC2"

libraryDependencies += "org.clapper" % "grizzled-scala_2.10" % "1.1.4"

libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.4.7"


libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

scalacOptions ++= Seq("-deprecation", "-feature")

scalaVersion := "2.10.1"
