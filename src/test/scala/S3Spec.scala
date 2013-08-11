package com.zope.s3blobserver

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import java.io.{File, FileInputStream, FileOutputStream}
import org.mockito.Mockito._

class S3Spec extends SampleData {

  "An S3 interface" should "Support S3 download" in {

    val client = mock(classOf[AmazonS3Client])
    val s3object = mock(classOf[S3Object])
    when(client.getObject("mybucket", "mykey")).thenReturn(s3object)

    val (tmpfile, bytes) = make_tempfile()

    when(s3object.getObjectContent()).thenReturn(
      new com.amazonaws.services.s3.model.S3ObjectInputStream(
        new FileInputStream(tmpfile),
        new org.apache.http.client.methods.HttpGet()))

    val s3 = new S3(client, "mybucket")
    val f = new File("mydata")
    s3.get("mykey", f)

    check_stream(new FileInputStream(f), bytes)
  }

  it should "support upload" in {
    val client = mock(classOf[AmazonS3Client])
    val s3 = new S3(client, "mybucket")
    val f = new File("mydata")
    s3.put(f, "mykey")
    verify(client).putObject("mybucket", "mykey", f)
  }

  it should "support deletion" in {
    val client = mock(classOf[AmazonS3Client])
    val s3 = new S3(client, "mybucket")
    s3.delete("mykey")
    verify(client).deleteObject("mybucket", "mykey")
  }
} 
