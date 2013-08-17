package com.zope.s3blobserver

import akka.actor.ActorSystem
import java.io.{File, FileInputStream}
import java.lang.Thread
import scala.concurrent.{Await, Future}
import org.scalatest.BeforeAndAfter
import scala.concurrent.duration.Duration

class S3BlobCacheSpec extends SampleData with BeforeAndAfter {

  implicit val system = ActorSystem()
  import system.dispatcher

  var dir: File = null
  var cdir: File = null

  before {
    dir = grizzled.file.util.createTemporaryDirectory("test")
    cdir = new File(dir, "cache");
    assert(cdir.mkdir())
  }

  after {
    grizzled.file.util.deleteTree(dir)
  }

  def create(size: Int = 9) = new S3BlobCache(cdir, size)

  "An S3BlobCache" should "move source files into the cache" in {
    val (f, bytes) = make_tempfile(file=new File(dir, "testdata"))
    val cache = create()
    cache.move(f)
    val cached = wait(cache.cache("testdata") { new File("x") })
    check_stream(new FileInputStream(cached), bytes)
    expectResult(false) {f.exists}
  }

  it should "initialize itself on startup" in {
    val data = (for (i <- 0 until 9)
                yield make_tempfile(file=new File(cdir, i.toString))._2
    )
    val cache = create()
    for (i <- 0 until 9) {
      val cached = wait(cache.cache(i.toString) { new File("x") })
      check_stream(new FileInputStream(cached), data(i))
    }
  }

  it should "initialize itself on startup, but honor cache size" in {
    val names = for (i <- 0 until 9) yield i.toString
    val data = (
      for (name <- names)
      yield make_tempfile(file=new File(cdir, name),
                          size=1000000,
                          exact_size=true)._2
    )
    val cache = create(5)
    wait_until { cdir.listFiles.length == 5 }
    for (f <- cdir.listFiles) {
      assert(names contains f.getName)
      val cached = wait(cache.cache(f.getName) { new File("x") })
      check_stream(new FileInputStream(cached), data(f.getName.toInt))
    }
  }
}

class CopyS3BlobCacheSpec extends S3BlobCacheSpec {

  import system.dispatcher

  override def create(size: Int = 9) = new CopyS3BlobCache(cdir, size)

}
