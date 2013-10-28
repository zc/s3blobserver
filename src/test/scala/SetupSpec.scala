package com.zope.s3blobserver

import com.amazonaws.services.s3.AmazonS3Client
import java.io.ByteArrayInputStream
import java.io.File
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.{Watcher => ZKWatcher}
import org.apache.zookeeper.Watcher.Event.KeeperState.SyncConnected
import org.apache.zookeeper.ZooKeeper
import org.mockito.Matchers
import org.mockito.Mockito
import spray.client.pipelining.{Get, sendReceive}
import spray.http.HttpResponse

class SetupSpec extends
    org.scalatest.FlatSpec with
    Log4jTesting {

  def with_config(
    config: String
  )(
    func: (Setup, String, File, File, File, AmazonS3Client) => Unit
  ) = {
    Testing.with_tmpdir {
      tmpdir =>

      val blobdir = new File(tmpdir, "blobs")
      blobdir.mkdir()
      val cachedir = new File(tmpdir, "cache")
      cachedir.mkdir

      val config_file = new File(tmpdir, "test.conf")
      util.stream_to_file(
        new ByteArrayInputStream(
          config.
            replace("$tmpdir", tmpdir.toString).
            replace("$blobdir", blobdir.toString).
            replace("$cachedir", cachedir.toString).
            getBytes),
        config_file)

      ProductionBindings.modifyBindings {
        implicit module =>

        val s3 = Mockito.mock(classOf[AmazonS3Client])
        module.bind[AmazonS3Client] toSingleInstance s3

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
            assert(args(0).asInstanceOf[String] ==
                     "zookeeper.example.com:2181")
            args(2) match {
              case w: ZKWatcher => watcher = w
              case _ => assert(false)
            }
            zk
          })

        // finally, set things up.
        val setup = new Setup(Array(config_file.toString))
        
        // Get the server address via ZooKeeper registration
        Testing.wait_until("ZooKeeper client created") {
          watcher != null
        }
        var base: String = null
        Mockito.when(
          zk.create(
            Matchers.anyString(),
            Matchers.anyObject(),
            Matchers.anyObject(),
            Matchers.anyObject())
        ).thenAnswer(
          Answer[String] {
            inv =>
            val path = inv.getArguments()(0).asInstanceOf[String] 
            base = "http:/" + path.substring(5) + "/"
            path
          })
        watcher.process(new WatchedEvent(null, SyncConnected, null))

        func(setup, base, tmpdir, blobdir, cachedir, s3)

        // cleanup
        setup.system.shutdown()
      }
    }
  }

  "main.Setup" should "Assemble the main process from config" in {
    with_config("""
        akka.loglevel = ERROR
        s3blobserver {
          log4j = """"" + """"
             log4j.rootLogger=WARN, TEST
             log4j.appender.TEST=org.apache.log4j.FileAppender
             log4j.appender.TEST.File=$tmpdir/log
             log4j.appender.TEST.layout=org.apache.log4j.PatternLayout
             log4j.appender.TEST.layout.ConversionPattern=%-5p %c %m%n
             """"" + """"
          cache {
            same-file-system = true
            directory = $cachedir
            size = 100
          }
          s3 {
            bucket = mybucket
            prefix = test/
          }
          committed {
            directory = $blobdir
            age = 1s
            poll-interval = 1s
          }
          server {
            port = 0
            host = localhost
            path = /test
            zookeeper = "zookeeper.example.com:2181"
            zookeeper-data = somedata
          }
        }
        """
    ) {
      (setup, base, tmpdir, blobdir, cachedir, s3) =>

      assert(setup.cache.getClass eq classOf[S3BlobCache])

      // verify that the logging config was updated:
      val logging_s = new java.io.ByteArrayOutputStream()
      new org.apache.log4j.config.PropertyPrinter(
        new java.io.PrintWriter(logging_s))
      val logging = new java.util.Properties()
      logging.load(new java.io.ByteArrayInputStream(logging_s.toByteArray))
      expectResult("WARN, TEST") {
        logging.getProperty("log4j.rootLogger")
      }

      // Movers working:
      util.stream_to_file(new ByteArrayInputStream("blob1".getBytes),
                          new File(blobdir, "1.blob"))
      val blobfile1 =
        Testing.wait_until("1.blob moved") {
          new File(cachedir, "1.blob").exists
        }
      Mockito.verify(s3).putObject(
        "mybucket", "test/1.blob", new File(blobdir, "1.blob"))

      // Set up another sample "blob" (that will stay in the blob directory)
      util.stream_to_file(new ByteArrayInputStream("blob2".getBytes),
                          new File(blobdir, "2"))

      // We can fetch 1.blob and 2
      implicit val system = setup.system
      import system.dispatcher
      val client = sendReceive

      expectResult("blob1") {
        Testing.wait[HttpResponse](client(Get(base+"1.blob"))).entity.asString
      }

      expectResult("blob2") {
        Testing.wait[HttpResponse](client(Get(base+"2"))).entity.asString
      }
      assert(new File(blobdir, "2").exists &&
               ! new File(cachedir, "2").exists)

      // If we try to get a file that doesn't exist, we'll touch S3
      Mockito.when(s3.getObject("mybucket", "test/3")).thenThrow(
        new com.amazonaws.services.s3.model.AmazonS3Exception("nosuch"))
      expectResult(404) {
        Testing.wait[HttpResponse](client(Get(base+"3"))).status.intValue
      }
      Mockito.verify(s3).getObject("mybucket", "test/3")

      // The failure is logged
      Thread.sleep(100) // Logging is asynchronous. <shrug>
      val buffer = new Array[Byte](999)
      val read = new java.io.FileInputStream(new File(tmpdir, "log")).read(buffer)
      val log = new String(buffer.slice(0, read)).split("\n")(0) // first line
      assert(log.startsWith("ERROR"))
      assert(log.containsSlice("/3"))
      assert(log.endsWith(": wtf?"))
    }
  }

  it should "handle separate file systems and aws creds" in {
    with_config("""
        akka.loglevel = ERROR
        s3blobserver {
          log4j = """"" + """"
             log4j.rootLogger=WARN, TEST
             log4j.appender.TEST=org.apache.log4j.FileAppender
             log4j.appender.TEST.File=$tmpdir/log
             log4j.appender.TEST.layout=org.apache.log4j.PatternLayout
             log4j.appender.TEST.layout.ConversionPattern=%-5p %c %m%n
             """"" + """"
          cache {
            same-file-system = false
            directory = $cachedir
            size = 100
          }
          s3 {
            bucket = mybucket
            prefix = test/
          }
          committed {
            directory = $blobdir
            age = 1s
            poll-interval = 1s
          }
          server {
            port = 0
            host = localhost
            path = /test
            zookeeper = "zookeeper.example.com:2181"
            zookeeper-data = somedata
          }
        }
        aws {
          accessKeyId = ID
          secretKey = SECRET
        }
        """
    ) {
      (setup, base, tmpdir, blobdir, cachedir, s3) =>

      assert(setup.cache.getClass eq classOf[CopyS3BlobCache])
      expectResult("ID") { System.getProperty("aws.accessKeyId") }
      expectResult("SECRET") { System.getProperty("aws.secretKey") }
    }
  }
}
