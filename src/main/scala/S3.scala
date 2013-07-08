package com.zope.s3blobserver

import java.io.File

object S3 {
  val client = new com.amazonaws.services.s3.AmazonS3Client(
    new com.amazonaws.auth.DefaultAWSCredentialsProviderChain())

  def get(bucket: String, key: String, dest: File): Unit = {
    util.stream_to_file(client.getObject(bucket, key).getObjectContent(), dest)
  }

  def put(src: File, bucket: String, key: String): Unit = {
    client.putObject(bucket, key, src)
  }

  def delete(bucket: String, key: String): Unit = {
    client.deleteObject(bucket, key)
  }
}
