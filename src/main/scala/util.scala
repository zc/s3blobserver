package com.zope.s3blobserver

import scala.annotation.tailrec

object util {

  def stream_to_file(inp: java.io.InputStream, dest: java.io.File): Unit = {
    val buffer = new Array[Byte](8192)
    val outp = new java.io.FileOutputStream(dest)

    @tailrec
    def copy(): Unit = {
      val read = inp.read(buffer)
      if (read > 0) {
        outp.write(buffer, 0, read)
        copy()
      }
      else assert(read < 0)
    }

    copy()
    outp.close()
    inp.close()
  }

  def timeit(f: => Unit): Long = {
    val t = System.currentTimeMillis()
    f
    System.currentTimeMillis() - t
  }

  def load_log4j_properties_string(src: String): Unit = {
    val props = new java.util.Properties()
    props.load(new java.io.ByteArrayInputStream(src.getBytes))
    org.apache.log4j.PropertyConfigurator.configure(props)
  }

}
