package com.zope.s3blobserver

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import akka.routing.FromConfig

import java.io.File
import java.io.FileInputStream

import scala.concurrent.ExecutionContext

class Watcher(
  src: File,
  min_age: Int,
  cache: S3BlobCache,
  s3: S3) extends Actor {

  val mover = context.actorOf(
    Props(classOf[MoveActor], cache, s3).withRouter(FromConfig()), "mover")

  val filter = new java.io.FileFilter {
    def accept(path: File) =
      path.isFile &&
      System.currentTimeMillis() - path.lastModified > min_age
  }

  def check() {
    for (f <- src.listFiles(filter))
      mover ! f
  }

  def receive = {
    case _ ⇒ check()
  }

}

class MoveActor(
  cache: S3BlobCache,
  s3: S3
) extends Actor {

  import context.dispatcher

  def move(src: File): Unit = {
    val name = src.getName
    s3.put(src, name)
    cache.move(src)
  }

  def receive = {
    case src: File ⇒ move(src)
  }
}

// trait S3BobServer(
//   commited: File,
//   cache: S3BlobCache
// )(
//   implicit ec: ExecutionContext
// ) extends HttpService {

//   // We can't use getFromFile here, because it opens the file in a
//   // separate thread. If there's a file-not-found exception, we
//   // wouldn't be able to catch it and fall back to the cache.  So we
//   // open the file before detatching.
//   def get_from_file(
//     file: File)(
//     implicit settings: RoutingSettings,
//     refFactory: ActorRefFactory
//   ): Unit = {
//     import spray.routing.BasicMarshallers.byteArrayMarshaller
//     val size = file.length;
//     val inp = new FileInputStream(file)
//     // We've opened the file, so from here, we're golden
//     (get & detachTo(spray.routing.singleRequestServiceActor)) {
//       implicit val bufferMarshaller = byteArrayMarshaller(contentType)
//       if (0 < settings.fileChunkingThresholdSize &&
//             settings.fileChunkingThresholdSize <= size
//       )
//         complete(inp.toByteArrayStream(settings.fileChunkingChunkSize.toInt))
//       else
//         complete(FileUtils.readAllBytes(file))
//     }
//   }

//   val blobRoute = {
//     get {
//       path(Segment) {
//         file_name =>
//         try {
//           // return the committed file. If this succeeds, were good
//           // even if the file gets moved, cuz we're on Unix.
//           get_from_file(new File(committed_directory, file_name))
//         }
//         catch {
//           case e: java.io.FileNotFoundException =>
//             cache(file_name) {
//               future_file =>
//               future_file.onComplete {
//                 case Success(file) =>
//                   // Concievably, the file could be evicted between
//                   // when it's accessed and when we open it. However,
//                   // since we have an lru cache, accessing it should
//                   // make eviction clase to impossible, unless the
//                   // cache is way too small.

//                   // We can use getFromFile here, because we don't
//                   // need to fall back.
//                   getFromFile(file)
//                 case Failure => reject
//               }
//             }
//         }
//       }
//     }

// }
