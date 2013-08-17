package com.zope.s3blobserver

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestActorRef
import com.typesafe.config.ConfigFactory
import java.io.{File, FileInputStream}
import org.mockito.Mockito._

class WatcherSpec extends SampleData {

  "A Watcher" should "Send old files to the cache when sent a message" in {

    implicit val system = ActorSystem(
      "test", ConfigFactory.parseString("""
        akka.actor.deployment {
          "/watcher/mover" {
            router = round-robin
            nr-of-instances = 2
          }
        }
    """))

    implicit val dispatcher = system.dispatcher

    // We start by setting up a source folder, with some files in it.
    val src = grizzled.file.util.createTemporaryDirectory("testsrc")
    val old_data = for (i <- 0 until 3) yield {
      val (f, b) = make_tempfile(file = new File(src, i.toString))
      f.setLastModified(System.currentTimeMillis() - 300*1000)
      b
    }
    for (i <- 3 until 6) {
      make_tempfile(file = new File(src, i.toString))
    }

    // And now a cache
    val cdir = grizzled.file.util.createTemporaryDirectory("testcdir")
    val cache = new S3BlobCache(cdir, 99)

    // An s3 mock
    val s3 = mock(classOf[S3])

    // A watcher
    val watcher = system.actorOf(Props(classOf[Watcher],
                                       src, 10*1000, cache, s3), "watcher")

    // Finally send it a message. Anything will do:
    watcher ! 42

    // There be threads involved, what with using a real actor system
    // and the futures (in the file cache), so we have to wait a bit.
    wait_until {cache.cache.count == 3}

    // Now, the old files should have been moved over and copied to s3
    for (i <- 0 until 3) {
      val cached = wait(cache.cache(i.toString) { new File("x") })
      check_stream(new FileInputStream(cached), old_data(i))
      val srcf = new File(src, i.toString)
      verify(s3).put(srcf, i.toString)
      assert(! srcf.exists)
    }
    verifyNoMoreInteractions(s3)

    // And the new files should still be there.
    for (i <- 3 until 6)
      assert(new File(src, i.toString).exists)

    // Running the watcher again, doesn't change anything
    watcher ! "ha"
    verifyNoMoreInteractions(s3)
    assert(cache.cache.count == 3)
    for (i <- 3 until 6)
      assert(new File(src, i.toString).exists)

    // clean up
    grizzled.file.util.deleteTree(src)
    grizzled.file.util.deleteTree(cdir)

    system.shutdown()
  }
}
