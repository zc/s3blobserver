package com.zope.s3blobserver

import java.io.{File, FileInputStream}
import scala.concurrent.Future
import spray.httpx.marshalling.BasicMarshallers.ByteArrayMarshaller
import spray.util.pimpInputStream

abstract class S3BlobServer(
  val committed: File,  val cache: S3BlobCache, val s3: S3
) extends spray.routing.HttpService {

  def file_data(
    inp: FileInputStream, size: Long)(
    implicit settings: spray.routing.RoutingSettings) =
  {
    if (0 < settings.fileChunkingThresholdSize &&
          settings.fileChunkingThresholdSize <= size)
      Left(inp.toByteArrayStream(settings.fileChunkingChunkSize.toInt))
    else
      Right(org.parboiled.common.FileUtils.readAllBytes(inp))
  }

  val route = {
    rejectEmptyResponse {
      get {
        path(Segment) {
          file_name =>

          implicit val dispatcher = actorRefFactory.dispatcher
          try {
            // return the committed file. If this succeeds, were good
            // even if the file gets removed, cuz we're on Unix.
            val file = new File(committed, file_name)
            val size = file.length;
            val inp = new FileInputStream(file)
            complete(Future { file_data(inp, size) })
          }
          catch {
            case e: java.io.FileNotFoundException =>
              // Not in committed
              // Check the cache, which may downloaf from s3.
              complete(
                cache(file_name, s3) map {
                  file =>
                  if (file == null)
                    // Couldn't find it anywhere, return empty, which 404s
                    Right(Array[Byte]())
                  else
                    file_data(new FileInputStream(file), file.length)
                })
          }
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
