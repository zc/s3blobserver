package com.zope.s3blobserver

import java.io.File
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

object Testing {

  def with_tmpdir(func: File => Unit) = {
    val dir = grizzled.file.util.createTemporaryDirectory("test")
    try func(dir)
    finally grizzled.file.util.deleteTree(dir)
  }

  def wait_until(label: String)(f: => Boolean): Unit = {
    for (i <- 0 until 3000) {
      if (f) {
        return
      }
      Thread.sleep(10)
    }
    assert(false, "timed out "+label)
  }

  def wait[T](future: Future[T]) =
    Await.result(future, Duration(9999, "millis"))
}
