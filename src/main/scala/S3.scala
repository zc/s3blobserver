package com.zope.s3blobserver

import com.amazonaws.services.s3.AmazonS3Client
import java.io.File

object S3 {
  def default_client = new AmazonS3Client(
    new com.amazonaws.auth.DefaultAWSCredentialsProviderChain())
}

class S3(client: AmazonS3Client, bucket: String, prefix: String = "") {

  def get(key: String, dest: File): Unit = {
    util.stream_to_file(
      client.getObject(bucket, prefix+key).getObjectContent(), dest)
  }

  def put(src: File, key: String): Unit = {
    client.putObject(bucket, prefix+key, src)
  }

  def delete(key: String): Unit = {
    client.deleteObject(bucket, prefix+key)
  }
}
