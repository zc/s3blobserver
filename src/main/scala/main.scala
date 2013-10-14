package com.zope.s3blobserver

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import java.io.File
import scala.concurrent.duration._
import spray.can.Http
import com.typesafe.config.ConfigFactory

object ProductionBindings extends
    com.escalatesoft.subcut.inject.NewBindingModule(module => {})

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

  val s3 = new S3(
    S3.default_client,
    config.getString("s3.bucket"),
    if (config.hasPath("s3.prefix")) config.getString("s3.prefix") else ""
    )

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

  implicit val timeout = akka.util.Timeout(1000)
  implicit val bindingModule = ProductionBindings
  var zookeeper_registration: ZooKeeperRegistration = null

  val service = system.actorOf(
    Props(classOf[S3BlobServerActor], committed, cache, s3),
    name)
  (akka.io.IO(Http) ? Http.Bind(
    service,
    config.getString("server.host"),
    port = config.getInt("server.port")
   )) onSuccess {
    case bound: akka.io.Tcp.Bound =>
      zookeeper_registration = new ZooKeeperRegistration(
        config.getString("server.path") + "/" +
          config.getString("server.host") + ":" +
          bound.localAddress.getPort,
        config.getString("server.zookeeper")
      )
  }
}
