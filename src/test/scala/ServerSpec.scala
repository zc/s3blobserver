package com.zope.s3blobserver

import java.io.File
import grizzled.file.util.createTemporaryDirectory
import org.mockito.Mockito.{doThrow, mock, verify, verifyNoMoreInteractions}
import org.mockito.Mockito.{when}

class TestServer(
  committed: File,
  cache: S3BlobCache,
  s3: S3,
  val arf: akka.actor.ActorRefFactory
) extends S3BlobServer(committed, cache, s3) {
  def actorRefFactory = arf
}


class ServerSpec extends
 SampleData with
 spray.testkit.ScalatestRouteTest with
 org.scalatest.BeforeAndAfter {

  var server: TestServer = null;

  before {
    server = new TestServer(
      grizzled.file.util.createTemporaryDirectory("testcommitted"),
      new S3BlobCache(grizzled.file.util.createTemporaryDirectory("testcache"),
                      9),
      mock(classOf[S3]),
      system)
  }

  after {
    grizzled.file.util.deleteTree(server.committed)
    grizzled.file.util.deleteTree(server.cache.directory)
  }


  "An S3blobserver" should "serve small files from the committed directory" in {

    val (tmpf, bytes) = make_tempfile(
      file = new File(server.committed, "smallcommitted"),
      delete = false, exact_size = true)

    Get("/smallcommitted") ~> server.routes ~> check {
      assert(entityAs[Array[Byte]].deep == bytes.deep)
    }
  }

  it should "server small files from cache hits" in {

    val (tmpf, bytes) = make_tempfile(
      file = new File(server.committed, "smallcached"),
      delete = false, exact_size = true)
    server.cache.move(tmpf)
    assert(new File(server.cache.directory, "smallcached").exists)

    Get("/smallcached") ~> server.routes ~> check {
      assert(entityAs[Array[Byte]].deep == bytes.deep)
    }
  }

  it should "404 when there's an error" in {

    doThrow(new com.amazonaws.services.s3.model.AmazonS3Exception("")
    ).when(server.s3).get("x", new File(server.cache.directory, "x.tmp"));

    Get("/x") ~> server.sealRoute(server.routes) ~> check {
      assert(status === spray.http.StatusCodes.NotFound)
    }

  }

}
