package com.zope.s3blobserver

import akka.actor.{Actor, Props}
import akka.event.Logging
import java.io.File

class Watcher(
  src: File,
  min_age: Int,
  cache: S3BlobCache,
  s3: S3) extends Actor {

  val mover = context.actorOf(
    Props(classOf[MoveActor], cache, s3).withRouter(
      akka.routing.FromConfig()),
    "mover")

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

