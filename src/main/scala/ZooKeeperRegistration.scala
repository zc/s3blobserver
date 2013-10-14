/** Register a server with ZooKeeper
  * 
  *  This registers a path as an ephemeral node.  If we get
  *  disconnected, the ZooKeeper client will try to reconnect.  If the
  *  session expires, we reconnect and re-register.
  * 
  *  TODO: We have a problem here, which is that we don't know if the
  *  session has expired until we reconnect, but if we don't
  *  reconenct, we're unregistered and don't know it.  (We're in the
  *  same boat with other ZK apps :().  When we disconnect, we should
  *  set a timer and if the timer goes off without is reconnecting we
  *  should do something drastic, like restarting or asking for help.
  * 
  */

package com.zope.s3blobserver

import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.Watcher.Event.KeeperState.Expired
import org.apache.zookeeper.Watcher.Event.KeeperState.SyncConnected
import org.apache.zookeeper.ZooKeeper
import scala.collection.JavaConverters.bufferAsJavaListConverter

class ZooKeeperFactory {
  def apply(connection_string: String,
             session_timeout: Int,
             watcher: org.apache.zookeeper.Watcher) =
    new ZooKeeper(connection_string, session_timeout, watcher)
}

class ZooKeeperRegistration(
  path: String,
  connection_string: String = "127.0.0.1:2181",
  session_timeout: Int = 4000
) (
  implicit val bindingModule: com.escalatesoft.subcut.inject.BindingModule
) extends
    com.escalatesoft.subcut.inject.Injectable {

  val factory = injectOptional [ZooKeeperFactory] getOrElse {
    new ZooKeeperFactory }

  var registered = false
  var zk: ZooKeeper = null

  val acls = scala.collection.mutable.ListBuffer(
    new org.apache.zookeeper.data.ACL(
      org.apache.zookeeper.ZooDefs.Perms.ALL,
      org.apache.zookeeper.ZooDefs.Ids.ANYONE_ID_UNSAFE)
  )

  val watcher = new org.apache.zookeeper.Watcher() {
      override def process(event: WatchedEvent) = {
        if (event.getState == SyncConnected && ! registered)
          register()
        else if (event.getState == Expired) {
          registered = false
          connect()
        }
      }
    }

  def connect(): Unit =
    zk = factory(connection_string, session_timeout, watcher)

  def register(): Unit = {
    zk.create(path, new Array[Byte](0), acls.asJava, CreateMode.EPHEMERAL)
    registered = true
  }

  connect()
}
