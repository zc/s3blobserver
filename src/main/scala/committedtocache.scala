// Moving files from a committed directory to S3 and then to the cache.

// A watcher actor is periodically woken up and scans the committed
// directory for old .blob files. When it sees one, it sends it to the
// worker pool.

// Workers take care of sending the file to S3 and then passing it to
// the cache.

// It's possible that the watcher will run while the workers are still
// processing the last scan, and that, as a result, a file previously
// sent to the workers will be sent again.  A worker will then have to
// process a file that doesn't exist.

package com.zope.s3blobserver

import akka.actor.{Actor, Props}
import akka.event.Logging
import java.io.File

class Watcher(
  src: File, min_age: Long, cache: S3BlobCache, s3: S3) extends Actor {

  val mover = context.actorOf(
    Props(classOf[MoveActor], cache, s3).withRouter(
      akka.routing.FromConfig()),
    "mover")

  val filter = new java.io.FileFilter {
    def accept(path: File) = (
      path.isFile &&
        path.getName.endsWith(".blob") &&
        System.currentTimeMillis() - path.lastModified > min_age
    )
  }

  def receive = {
    case _ ⇒
      for (f <- src.listFiles(filter))
        mover ! f
  }
}

class MoveActor(cache: S3BlobCache, s3: S3) extends Actor {

  import context.dispatcher

  def receive = {
    case src: File ⇒
      if (src.exists)
        try {
          s3.put(src, src.getName)
          cache.move(src)
        }
        catch {
          case _: com.amazonaws.AmazonClientException =>
          case _: java.io.FileNotFoundException =>
          // The file went away because another mover handled it. <shrug>
        }
  }
}

