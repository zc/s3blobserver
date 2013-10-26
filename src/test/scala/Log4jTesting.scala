// Test helper trait that saves and restores log4j logging config and
// also makes logging silent when not configured.
package com.zope.s3blobserver

trait Log4jTesting extends org.scalatest.AbstractSuite {
  this: org.scalatest.Suite =>

  val saved = new java.io.ByteArrayOutputStream()
  new org.apache.log4j.config.PropertyPrinter(new java.io.PrintWriter(saved))

  def nullify_logging() = util.load_log4j_properties_string("""
    log4j.rootLogger=OFF, NULL
    log4j.appender.NULL=org.apache.log4j.varia.NullAppender
    """)

  nullify_logging()

  abstract override def withFixture(test: NoArgTest) {
    nullify_logging()
    try super.withFixture(test)
    finally util.load_log4j_properties_string(saved.toString)
  }
}
