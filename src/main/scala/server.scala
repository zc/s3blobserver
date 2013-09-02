package com.zope.s3blobserver

import java.io.{File, FileInputStream}

abstract class S3BlobServer(
  val committed: File,
  val cache: S3BlobCache,
  val s3: S3
) extends spray.routing.HttpService {

  // We can't use getFromFile here, because it opens the file in a
  // separate thread. If there's a file-not-found exception, we
  // wouldn't be able to catch it and fall back to the cache.  So we
  // open the file before detatching.
  def get_from_file_or_throw(
    file: File)(
    implicit settings: spray.routing.RoutingSettings
  ): spray.routing.Route = {
    import spray.httpx.marshalling.BasicMarshallers.byteArrayMarshaller
    import spray.util.pimpInputStream
    val size = file.length;
    val inp = new FileInputStream(file)
    // We've opened the file, so from here, we're golden
    (get & detachTo(singleRequestServiceActor)) {
      implicit val bufferMarshaller = byteArrayMarshaller(
        spray.http.MediaTypes.`application/octet-stream`)
      if (0 < settings.fileChunkingThresholdSize &&
            settings.fileChunkingThresholdSize <= size
      )
        complete(inp.toByteArrayStream(settings.fileChunkingChunkSize.toInt))
      else
        complete(org.parboiled.common.FileUtils.readAllBytes(file))
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
          case e: java.io.FileNotFoundException =>
            {
              ctx: spray.routing.RequestContext =>
              println("trying to get ", file_name)
              implicit val dispatcher = actorRefFactory.dispatcher
              cache(file_name, s3).onComplete {
                case scala.util.Success(file) =>
                  // Concievably, the file could be evicted between
                  // when it's accessed and when we open it. However,
                  // since we have an lru cache, accessing it should
                  // make eviction clase to impossible, unless the
                  // cache is way too small.

                  // We can use getFromFile here, because we don't
                  // need to fall back.
                  println("from cache", file)
                  getFromFile(file)
                case _ =>
                  println("waaa, reject")
                  reject
              }
            }
          case _: Throwable =>
              reject
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
