package com.zope.s3blobserver

import akka.event.Logging
import java.io.{File, FileInputStream}
import scala.concurrent.Future
import spray.http.{HttpRequest, HttpResponse}
import spray.httpx.marshalling.BasicMarshallers.ByteArrayMarshaller
import spray.util.pimpInputStream
import spray.routing.directives.LogEntry
import spray.routing.RoutingSettings


abstract class S3BlobServer(
  val committed: File,  val cache: S3BlobCache, val s3: S3
) extends spray.routing.HttpService {

  val route = {
    dynamic {
      // dynamic is needed to cause logRequestResponse to be
      // executed for each request.  Othwise an "optimization"
      // causes it to happen only once before any requests.
      logRequestResponse(showResponses _) {
        get {
          path("ruok") {
            complete { "imok" }
          } ~
          path(Segment) {
            file_name =>

            implicit val dispatcher = actorRefFactory.dispatcher
            onSuccess(
              Future {
                val file = new File(committed, file_name)
                val size = file.length;
                (new FileInputStream(file), size)
              } recoverWith {
                case e: java.io.FileNotFoundException =>
                  cache(file_name, s3) map {
                    file =>
                    if (file == null)
                      (null, 0.asInstanceOf[Long])
                    else
                    {
                      val size = file.length;
                      (new FileInputStream(file), size)
                    }
                  }
              }
            ) {
              case (inp, size) =>
                val settings = RoutingSettings.default
                if (inp == null)
                  reject
                else if (size > settings.fileChunkingThresholdSize)
                  complete(
                    inp.toByteArrayStream(settings.fileChunkingChunkSize.toInt)
                  )
                else
                  complete(org.parboiled.common.FileUtils.readAllBytes(inp))
            }
          }
        }
      }
    }
  }

  def showResponses(request: HttpRequest): Any ⇒ Option[LogEntry] = {
    // Rudimentry access logging, only for debugging.
    case response: HttpResponse =>
      Some(LogEntry(request.uri + ": " + response.status, Logging.DebugLevel))
    case _ =>
      Some(LogEntry(request + ": wtf?", Logging.ErrorLevel))
  }
}

class S3BlobServerActor(
  committed: File, cache: S3BlobCache, s3: S3
) extends
    S3BlobServer(committed, cache, s3) with
    akka.actor.Actor {
  def actorRefFactory = context
  def receive = runRoute(route)
}
