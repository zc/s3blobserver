package com.zope.s3blobserver

import akka.actor.ActorSystem
import akka.testkit.TestActorRef
import java.io.File
import org.mockito.Mockito._

class MoveSpec extends org.scalatest.FlatSpec {

  "A MoveActor" should "Move files" in {

    val s3 = mock(classOf[S3])
    val cache = mock(classOf[S3BlobCache])
    implicit val system = ActorSystem("test")
    val mover = TestActorRef(new MoveActor(cache, s3))
    val f = new File("t")
    mover ! f
    val inorder = inOrder(s3, cache)
    inorder.verify(s3).put(f, "t")
    inorder.verify(cache).move(f)
    inorder.verifyNoMoreInteractions()
  }
}
