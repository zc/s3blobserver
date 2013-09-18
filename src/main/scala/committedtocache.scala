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
    def accept(path: File) =
      path.isFile &&
      System.currentTimeMillis() - path.lastModified > min_age
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
      s3.put(src, src.getName)
      cache.move(src)
  }
}

