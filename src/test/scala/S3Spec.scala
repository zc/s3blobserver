package com.zope.s3blobserver

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import java.io.{File, FileInputStream, FileOutputStream}
import org.mockito.Mockito

class S3Spec extends SampleData {

  "An S3 interface" should "Support S3 download" in {

    val client = Mockito.mock(classOf[AmazonS3Client])
    val s3object = Mockito.mock(classOf[S3Object])
    Mockito.when(
      client.getObject("mybucket", "myprefixmykey")
    ).thenReturn(s3object)

    val (tmpfile, bytes) = make_tempfile()

    Mockito.when(s3object.getObjectContent()).thenReturn(
      new com.amazonaws.services.s3.model.S3ObjectInputStream(
        new FileInputStream(tmpfile),
        new org.apache.http.client.methods.HttpGet()))

    val s3 = new S3(client, "mybucket", "myprefix")
    val f = new File("mydata")
    s3.get("mykey", f)

    check_stream(new FileInputStream(f), bytes)
  }

  it should "support upload" in {
    val client = Mockito.mock(classOf[AmazonS3Client])
    val s3 = new S3(client, "mybucket", "myprefix")
    val f = new File("mydata")
    s3.put(f, "mykey")
    Mockito.verify(client).putObject("mybucket", "myprefixmykey", f)
  }

  it should "support deletion" in {
    val client = Mockito.mock(classOf[AmazonS3Client])
    val s3 = new S3(client, "mybucket", "myprefix")
    s3.delete("mykey")
    Mockito.verify(client).deleteObject("mybucket", "myprefixmykey")
  }
} 
