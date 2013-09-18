package com.zope.s3blobserver

import akka.actor.{ActorSystem, Props}
import java.io.File
import scala.concurrent.duration._
import spray.can.Http
import com.typesafe.config.ConfigFactory

object Main extends App {
  val name = "s3blobserver"

  implicit val system = ActorSystem(
    name+"-system",
    ConfigFactory.load(ConfigFactory.parseFile(new File(args(0))))
  )
  implicit val dispatcher = system.dispatcher
  val config = system.settings.config.getConfig(name)

  val cache = S3BlobCache(
    config.getBoolean("cache.same-file-system"),
    new File(config.getString("cache.directory")),
    config.getInt("cache.size"))

  val s3 = new S3(S3.default_client, config.getString("s3.bucket"))

  val committed = new File(config.getString("committed.directory"))

  val watcher = system.actorOf(
    Props(classOf[Watcher],
          committed, config.getMilliseconds("committed.age"), cache, s3),
    "watcher")
  system.scheduler.schedule(
    0.millisecond,
    config.getMilliseconds(
      "committed.poll-interval").toInt.millisecond,
    watcher,
    "")

  val service = system.actorOf(
    Props(classOf[S3BlobServerActor], committed, cache, s3),
    name)
  akka.io.IO(Http) ! Http.Bind(
    service,
    config.getString("server.host"),
    port = config.getInt("server.port")
  )
}
