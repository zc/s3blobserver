package com.zope.s3blobserver

import java.io.{File, FileInputStream}
import spray.httpx.marshalling.BasicMarshallers.ByteArrayMarshaller
import spray.util.pimpInputStream

abstract class S3BlobServer(
  val committed: File,
  val cache: S3BlobCache,
  val s3: S3
) extends spray.routing.HttpService {


  def complete_with_stream(
    inp: FileInputStream, size: Long
  )(
    implicit settings: spray.routing.RoutingSettings
  ) = {
    if (0 < settings.fileChunkingThresholdSize &&
          settings.fileChunkingThresholdSize <= size
    )
      complete(inp.toByteArrayStream(settings.fileChunkingChunkSize.toInt))
    else
      complete(org.parboiled.common.FileUtils.readAllBytes(inp))
  }

  // We can't use getFromFile here, because it opens the file in a
  // separate thread. If there's a file-not-found exception, we
  // wouldn't be able to catch it and fall back to the cache.  So we
  // open the file before detatching.
  def get_from_file_or_throw(
    file: File
  )(
    implicit settings: spray.routing.RoutingSettings
  ): spray.routing.Route = {
    val size = file.length;
    val inp = new FileInputStream(file)
    // We've opened the file, so from here, we're golden
    (get & detachTo(singleRequestServiceActor)) {
      complete_with_stream(inp, size)
    }
  }

  val routes = {
    get {
      path(Segment) {
        file_name =>
        try {
          // return the committed file. If this succeeds, were good
          // even if the file gets removed, cuz we're on Unix.
          get_from_file_or_throw(new File(committed, file_name))
        }
        catch {
          case e: java.io.FileNotFoundException => // Not in committed
            { // We're making a directive on the fly, so we can snag a context 
              ctx =>
              implicit val dispatcher = actorRefFactory.dispatcher
              // Check the cache, which may downloaf from s3.
              cache(file_name, s3) onComplete {
                case scala.util.Success(file) =>
                  try {
                    val route = complete_with_stream(
                      new FileInputStream(file), file.length)
                    route(ctx)
                  }
                  catch {
                    case _: Throwable => reject(ctx)
                  }
                case _ =>
                  reject(ctx)
              }
            }
          case _: Throwable => reject // wtf?
        }
      }
    }
  }
}

class S3BlobServerActor(
  committed: File,
  cache: S3BlobCache,
  s3: S3
) extends
    S3BlobServer(committed, cache, s3) with
    akka.actor.Actor {
  def actorRefFactory = context
  def receive = runRoute(routes)
}
