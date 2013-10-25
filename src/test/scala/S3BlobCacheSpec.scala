package com.zope.s3blobserver

import akka.actor.ActorSystem
import java.io.{File, FileInputStream}
import java.lang.Thread
import scala.concurrent.{Await, Future}
import org.mockito.Mockito
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

  it should "Download from S3 on a cache miss" in {

    val cache = create()

    // We're mocking S3, so it's not actually going to download
    // anything, so we create the file a real s3 would have
    // downloaded.
    val s3 = Mockito.mock(classOf[S3])
    val (tmpf, bytes) = make_tempfile(
      delete=false, file=new File(cache.directory, "x.tmp"))

    {
      // Now we try to access a file named "x"
      val future_file = cache("x", s3)

      // We got a file with the right data.
      check_stream(new FileInputStream(wait(future_file)), bytes)
    }

    // The tmp file has been consumed
    assert( ! tmpf.exists)

    // S3 was called as expected
    Mockito.verify(s3).get("x", tmpf)

    {
      // Now fetch again.  We'll get data from the cache and won't call
      // s3 again.
      val future_file = cache("x", s3)

      // We got a file with the right data.
      check_stream(new FileInputStream(wait(future_file)), bytes)
    }

    // s3 wasn't called:
    Mockito.verifyNoMoreInteractions(s3)
  }

  it should "return null if the file can't be downloaded from s3" in {
    val cache = create()
    val s3 = Mockito.mock(classOf[S3])

    Mockito.doThrow(
      new com.amazonaws.services.s3.model.AmazonS3Exception("")
    ).when(s3).get("m", new File(cache.directory, "m.tmp"));

    assert(wait(cache("m", s3)) === null)
  }

  it should "not fail when asked to move a file that's already moved" in {

    val cache = create()
    val name = "x.blob"
    new File(cache.directory, name).createNewFile()
    cache.move(new File(dir, name)) // Look ma, no exception
  }

  it should "fail when asked to move a file that doesn't exist" in {

    val cache = create()
    val name = "x.blob"

    intercept[java.lang.AssertionError] {
      cache.move(new File(dir, name))
    }
  }
}

class CopyS3BlobCacheSpec extends S3BlobCacheSpec {

  import system.dispatcher

  override def create(size: Int = 9) = new CopyS3BlobCache(cdir, size)

}
