package com.zope.s3blobserver

import java.io.File
import java.io.FileOutputStream
import java.lang.Thread
import scala.concurrent.{Await, Future}

class FileCacheSpec extends
    org.scalatest.FlatSpec with
    Log4jTesting with
    org.scalatest.BeforeAndAfter {

  implicit val system = akka.actor.ActorSystem()
  import system.dispatcher

  var dir: File = null
  val cache = new FileCache(1)

  before {
    dir = grizzled.file.util.createTemporaryDirectory("test")
  }

  after {
    cache.clear()
    grizzled.file.util.deleteTree(dir)
  }

  def make_file(name: String, size: Int): File = {
    val result = new File(dir, name)
    val ostream = new FileOutputStream(result)
    for (i <- 1 to size) ostream.write(0)
    result
  }

  def wait(future: Future[File]) = Await.result(
    future, scala.concurrent.duration.Duration(999, "millis"))

  "A FileCache" should "start empty" in {
    assert(cache.size == 0)
  }

  it should "Store files directly" in {
    val f = make_file("a", 1<<18)
    wait(cache("a")(f))
    Thread.sleep(10)
    expectResult (1 << 18) { cache.bytes }
  }

  it should "evict files when it gets too big" in {
    wait(cache("a")(make_file("a", 1<<19)))
    wait(cache("b")(make_file("b", 1<<19)))
    wait(cache("c")(make_file("c", 1<<19)))
    Thread.sleep(10)
    assert(! (new File(dir, "a").exists))
    assert(new File(dir, "b").exists)
    assert(new File(dir, "c").exists)
  }

  it should "evict files when it gets too big even in threads." in {
    for (i <- 0 until 10)
      make_file(i.toString, 200000)
    cache("0") {
      Thread.sleep(99)
      make_file("0", 1)
    }
    for (i <- 0 until 10)
      cache(i.toString) {
        new File(dir, i.toString)
      }

    Testing.wait_until("There are only 5 files in the directory") {
      dir.list.length == 5
    }
  }

  it should "store files via function" in {
    wait(cache("a") { make_file("a", 1<<18) })
    Thread.sleep(10)
    expectResult (1 << 18) { cache.bytes }
  }

  it should "not evaluate a function if the valus is already cached" in {
    val f = wait(cache("a")(make_file("a", 1<<19)))
    expectResult(f) {
      wait(
        cache("a") {
          assert(false)
          new File("x")
        })
    }
  }

  it should "evict failures (eventually)" in {
    intercept[RuntimeException] {
      wait(cache("a")((throw new RuntimeException("Naa")): File))
    }
    Testing.wait_until("the failure has been evicted") {
      cache.get("a").isEmpty
    }
  }

  it should "not overflow when computing it's capacity" in {
    val cache = new FileCache(99999)
    expectResult(12799872) { cache.store.capacity }
  }

  it should "run functions in parallel" in {

    val log = scala.collection.mutable.ListBuffer[Char]()
    val f1 = cache("a") {
      Thread.sleep(99)
      log += 'a'
      make_file("a", 1)
    }
    val f2 = cache("b") {
      log += 'b'
      make_file("b", 1)
    }
    wait(f2)
    wait(f1)

    expectResult("ba") {
      new String(log.toArray)
    }
  }
}
