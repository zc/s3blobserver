package com.zope.s3blobserver

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import java.io.File
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import spray.can.Http

object ProductionBindings extends
    com.escalatesoft.subcut.inject.NewBindingModule(module => {})

object Main extends App {
  implicit val bindingModule = ProductionBindings
  new Setup(args).system.awaitTermination()
  System.exit(1)
}

class Setup(
  args: Array[String],
  bind_timeout:Int = 9999
)(
  implicit val bindingModule: com.escalatesoft.subcut.inject.BindingModule
) extends
    com.escalatesoft.subcut.inject.Injectable {

  val name = "s3blobserver"

  // Set default logging config to handle log messages loading configuration :)
  // and to avoid warnings about logging not being configured.
  util.load_log4j_properties_string(
    """
    log4j.rootLogger=WARN, A1
    log4j.appender.A1=org.apache.log4j.ConsoleAppender
    log4j.appender.A1.layout=org.apache.log4j.PatternLayout
    log4j.appender.A1.layout.ConversionPattern=%d{ISO8601} %-5p %c %m%n
    """)

  implicit val system = ActorSystem(
    name+"-system",
    ConfigFactory.load(ConfigFactory.parseFile(new File(args(0))))
  )
  implicit val dispatcher = system.dispatcher
  val config = system.settings.config.getConfig(name)

  // Load the logging configuration (for real).
  util.load_log4j_properties_string(config.getString("log4j"))

  // aws creds
  if (system.settings.config.hasPath("aws")) {
    val aws = system.settings.config.getConfig("aws")
    System.setProperty("aws.accessKeyId", aws.getString("accessKeyId"))
    System.setProperty("aws.secretKey", aws.getString("secretKey"))
  }

  val cache = S3BlobCache(
    config.getBoolean("cache.same-file-system"),
    new File(config.getString("cache.directory")),
    config.getInt("cache.size"))

  val s3 = new S3(
    injectOptional[com.amazonaws.services.s3.AmazonS3Client] getOrElse {
      S3.default_client
    },
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

  implicit val timeout = akka.util.Timeout(bind_timeout)
  var zookeeper_registration: ZooKeeperRegistration = null

  val service = system.actorOf(
    Props(classOf[S3BlobServerActor], committed, cache, s3),
    name)

  val log = Logging(system, getClass)
  val bind_start_time = System.currentTimeMillis
  (akka.io.IO(Http) ? Http.Bind(
    service,
    config.getString("server.host"),
    port = config.getInt("server.port")
   )) onComplete {
    case Success(result) =>
      log.info(
        s"Bound $result in ${System.currentTimeMillis - bind_start_time}ms")
      result match {
        case bound: akka.io.Tcp.Bound =>
          if (config.hasPath("server.path") &&
                config.hasPath("server.zookeeper")) {
            zookeeper_registration = new ZooKeeperRegistration(
              config.getString("server.path") + "/" +
                config.getString("server.host") + ":" +
                bound.localAddress.getPort,
              config.getString("server.zookeeper"),
              data = (if (config.hasPath("server.zookeeper-data"))
                        config.getString("server.zookeeper-data")
                      else "")
            )
          }
        case wtf =>
          log.error(s"Bind failed: $wtf")
          system.shutdown()
      }
    case Failure(err) =>
      log.error(s"Bind failed: $err")
      system.shutdown()
  }
}
