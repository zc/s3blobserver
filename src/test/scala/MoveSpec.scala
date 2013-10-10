package com.zope.s3blobserver

import akka.actor.ActorSystem
import akka.testkit.TestActorRef
import java.io.File
import org.mockito.Mockito

class MoveSpec extends
    org.scalatest.FlatSpec with
    org.scalatest.BeforeAndAfter {

  var dir: File = null

  before {
    dir = grizzled.file.util.createTemporaryDirectory("test")
  }

  after {
    grizzled.file.util.deleteTree(dir)
  }

  "A MoveActor" should "Move files" in {

    val s3 = Mockito.mock(classOf[S3])
    val cache = Mockito.mock(classOf[S3BlobCache])
    implicit val system = ActorSystem("test")
    val mover = TestActorRef(new MoveActor(cache, s3))
    val f = new File(dir, "t")
    f.createNewFile()
    mover ! f
    val inorder = Mockito.inOrder(s3, cache)
    inorder.verify(s3).put(f, "t")
    inorder.verify(cache).move(f)
    inorder.verifyNoMoreInteractions()

  }

  it should "not freak if a file isn't there" in {
    val s3 = Mockito.mock(classOf[S3])
    val cache = Mockito.mock(classOf[S3BlobCache])
    implicit val system = ActorSystem("test")
    val mover = TestActorRef(new MoveActor(cache, s3))
    val f = new File(dir, "t")
    mover ! f
    val inorder = Mockito.inOrder(s3, cache)
    inorder.verifyNoMoreInteractions()
  }

  it should "not freak when aws throws" in {
    val s3 = Mockito.mock(classOf[S3])
    val cache = Mockito.mock(classOf[S3BlobCache])
    implicit val system = ActorSystem("test")
    val mover = TestActorRef(new MoveActor(cache, s3))
    val f = new File(dir, "t")
    f.createNewFile()
    Mockito.when(s3.put(f, "t")).thenThrow(
      new com.amazonaws.AmazonClientException("test"))
    mover ! f
    val inorder = Mockito.inOrder(s3, cache)
    inorder.verify(s3).put(f, "t")
    inorder.verifyNoMoreInteractions()
  }

  it should "not freak when file can't be opened when copying to the cache" in {
    val s3 = Mockito.mock(classOf[S3])
    val cache = Mockito.mock(classOf[S3BlobCache])
    implicit val system = ActorSystem("test")
    val mover = TestActorRef(new MoveActor(cache, s3))
    val f = new File(dir, "t")
    f.createNewFile()

    Mockito.doAnswer(
      new org.mockito.stubbing.Answer[Unit]() {
        def answer(invocation:
                       org.mockito.invocation.InvocationOnMock): Unit =
          throw new java.io.FileNotFoundException()
      }).when(cache).move(f)

    mover ! f
    val inorder = Mockito.inOrder(s3, cache)
    inorder.verify(s3).put(f, "t")
    inorder.verify(cache).move(f)
    inorder.verifyNoMoreInteractions()
  }

}
