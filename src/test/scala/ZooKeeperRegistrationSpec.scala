package com.zope.s3blobserver

import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.{Watcher => ZKWatcher}
import org.apache.zookeeper.Watcher.Event.KeeperState.Expired
import org.apache.zookeeper.Watcher.Event.KeeperState.SyncConnected
import org.apache.zookeeper.ZooKeeper
import org.mockito.Matchers
import org.mockito.Mockito
import scala.collection.JavaConverters.bufferAsJavaListConverter

class ZooKeeperRegistrationSpec extends org.scalatest.FlatSpec {

  val acls = scala.collection.mutable.ListBuffer(
    new org.apache.zookeeper.data.ACL(
      org.apache.zookeeper.ZooDefs.Perms.ALL,
      org.apache.zookeeper.ZooDefs.Ids.ANYONE_ID_UNSAFE)
  )

  "A Zookeeper registration" should "register with the given parameters" in {
    ProductionBindings.modifyBindings {
      implicit module =>

      val factory = Mockito.mock(classOf[ZooKeeperFactory])
      module.bind[ZooKeeperFactory] toSingleInstance factory

      val zk = Mockito.mock(classOf[ZooKeeper])
      var watcher: ZKWatcher = null

      Mockito.when(
        factory.apply(
          Matchers.anyString(), Matchers.anyInt(), Matchers.anyObject())
      ).thenAnswer(
        Answer[ZooKeeper] {
          inv =>
          val args = inv.getArguments()
          assert(args(0).asInstanceOf[String] == "zookeeper.example.com:2181")
          assert(args(1).asInstanceOf[Int] == 2000)
          args(2) match {
            case w: ZKWatcher => watcher = w
            case _ => assert(false)
          }
          zk
        })

      val registration = new ZooKeeperRegistration(
        "/foo/bar", "zookeeper.example.com:2181", 2000)

      assert(registration.zk eq zk)
      assert(! registration.registered)

      watcher.process(new WatchedEvent(null, SyncConnected, null))
      Mockito.verify(zk).create(
        "/foo/bar", new Array[Byte](0), acls.asJava, CreateMode.EPHEMERAL)

      assert(registration.registered)
    }
  }

  it should "provide handy defaults" in {
    ProductionBindings.modifyBindings {
      implicit module =>

      val factory = Mockito.mock(classOf[ZooKeeperFactory])
      module.bind[ZooKeeperFactory] toSingleInstance factory

      val zk = Mockito.mock(classOf[ZooKeeper])
      var watcher: ZKWatcher = null

      Mockito.when(
        factory.apply(
          Matchers.anyString(), Matchers.anyInt(), Matchers.anyObject())
      ).thenAnswer(
        Answer[ZooKeeper] {
          inv =>
          val args = inv.getArguments()
          assert(args(0).asInstanceOf[String] == "127.0.0.1:2181")
          assert(args(1).asInstanceOf[Int] == 4000)
          args(2) match {
            case w: ZKWatcher => watcher = w
            case _ => assert(false)
          }
          zk
        })

      val registration = new ZooKeeperRegistration("/foo/bar")
    }
  }

  it should "reconnect and register when a session has expired" in {
    ProductionBindings.modifyBindings {
      implicit module =>

      val factory = Mockito.mock(classOf[ZooKeeperFactory])
      module.bind[ZooKeeperFactory] toSingleInstance factory

      val zk = Mockito.mock(classOf[ZooKeeper])
      val zk2 = Mockito.mock(classOf[ZooKeeper])
      var watcher: ZKWatcher = null
      var first = true

      Mockito.when(
        factory.apply(
          Matchers.anyString(), Matchers.anyInt(), Matchers.anyObject())
      ).thenAnswer(
        Answer[ZooKeeper] {
          inv =>
          val args = inv.getArguments()
          assert(args(0).asInstanceOf[String] == "127.0.0.1:2181")
          assert(args(1).asInstanceOf[Int] == 4000)
          args(2) match {
            case w: ZKWatcher => watcher = w
            case _ => assert(false)
          }
          if (first) zk else zk2
        })

      val registration = new ZooKeeperRegistration("/foo/bar")

      assert(registration.zk eq zk)
      assert(! registration.registered)

      watcher.process(new WatchedEvent(null, SyncConnected, null))

      Mockito.verify(zk).create(
        "/foo/bar", new Array[Byte](0), acls.asJava, CreateMode.EPHEMERAL)

      assert(registration.registered)

      first = false
      watcher.process(new WatchedEvent(null, Expired, null))

      assert(registration.zk eq zk2)
      assert(! registration.registered)

    }

  }

}
