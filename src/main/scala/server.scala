package com.zope.s3blobserver

import java.io.{File, FileInputStream}
import scala.concurrent.Future
import spray.httpx.marshalling.BasicMarshallers.ByteArrayMarshaller
import spray.util.pimpInputStream

abstract class S3BlobServer(
  val committed: File,  val cache: S3BlobCache, val s3: S3
) extends spray.routing.HttpService {

  val route = {
    get {
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
            val settings = spray.routing.RoutingSettings.default
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

class S3BlobServerActor(
  committed: File, cache: S3BlobCache, s3: S3
) extends
    S3BlobServer(committed, cache, s3) with
    akka.actor.Actor {
  def actorRefFactory = context
  def receive = runRoute(route)
}
