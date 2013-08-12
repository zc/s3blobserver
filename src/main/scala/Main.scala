package com.zope.s3blobserver

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import java.io.File
import java.io.FileInputStream

import scala.concurrent.ExecutionContext

// object MoveActor {
//   def props(
//     cache: FileCache,
//     s3: S3
//   ): Props = Props(classOf[Mover], cache, s3)
// }


class S3BlobCache(directory: File, val cache: FileCache)
                 (implicit ec: ExecutionContext) {
  // A disk cache to which files can be moved.
  // IOW, source files must be on the same file system.

  def this(directory: File, capacity_megabytes: Int)
          (implicit ec: ExecutionContext) =
    this(directory, new FileCache(capacity_megabytes))

  // Load existing files into the cache
  for (f <- directory.listFiles)
    if (f.isFile)
      cache.set(f.getName, f)

  def move(src: File) : Unit = {
    val name = src.getName
    val dest = new File(directory, name)
    assert(src.renameTo(dest))
    cache.set(name, dest)
  }
}

class CopyS3BlobCache(directory: File, cache: FileCache)
                     (implicit ec: ExecutionContext) extends
    S3BlobCache(directory, cache) {

  def this(directory: File, capacity_megabytes: Int)
          (implicit ec: ExecutionContext) =
    this(directory, new FileCache(capacity_megabytes))

  // A disk cache to which files must be copies.
  // IOW, source files are on a different file system.

  override def move(src: File) : Unit = {
    val name = src.getName
    val dest = new File(directory, name)
    util.stream_to_file(new FileInputStream(src), dest)
    assert(src.renameTo(dest))
    cache.set(name, dest)
  }
}

// trait Watcher(src: File, min_age: Int, mover: ActorRef) {

//   val filter = new java.io.FileFilter {
//     def accept(path: File) =
//       path.isFile &&
//       System.currentTimeMillis() - path.lastModified > min_age
//   }

//   def check() {
//     for (f <- src.listFiles(filter))
//       mover tell f
//   }

// }

class MoveActor(
  cache: S3BlobCache,
  s3: S3
) extends Actor {

  import context.dispatcher

  val log = Logging(context.system, this)

  def move(src: File): Unit = {
    val name = src.getName
    s3.put(src, name)
    cache.move(src)
  }

  def receive = {
    case src: File ⇒ move(src)
    case _ ⇒ log.info("received unknown message")
  }
}

// trait S3BobServer(
//   s3: S3,
//   cache: FileCache,
//   commit_directory: File,
//   cache_directory: File,
// ) extends HttpService {

//   def this(
//     committed_directory: File,
//     cache_directory: File,
//     bucket: String
//   ) = {

//   }

//   implicit def executionContext = actorRefFactory.dispatcher

//   val blobRoute = {

//     get {
//       path(Segment) {
//         file_name =>

//         try {
//           // open the committed file. If this succeeds, were good even
//           // if the file gets moved, cuz we're on Unix.
//           val stream = new FileInputStream(
//             new File(committed_directory, file_name))
//           // 200 return data
//         }
//         catch {
//           case e: java.io.FileNotFoundException =>

//             val future_file = cache(file_name) {
//               val cached = new File(cache_dir, file_name)
//               S3.get(bucket_name, file_name, cached)
//               cached
//             }

//           future_file.onComplete {
//             case Success(f) =>
//               // There's a potential race here.  Concievably, the
//               // file could be evicted between the time it's added and
//               // The time we open it.
//               val stream = new FileInputStream(f)
//               // 200 return data
//             case Failure(e) => // 404 wtf
//           }
//         }

//       }

//     }


//   }

// }
