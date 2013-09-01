package com.zope.s3blobserver

import java.io.{File, FileInputStream}
import scala.concurrent.{ExecutionContext, Future}

class S3BlobCache(
  val directory: File,
  val cache: FileCache
)(
  implicit ec: ExecutionContext
) {
  // A disk cache to which files can be moved.
  // IOW, source files must be on the same file system.

  def this(
    directory: File, capacity_megabytes: Int
  )(implicit ec: ExecutionContext) = {

    this(directory, new FileCache(capacity_megabytes))
  }

  // Load existing files into the cache
  for (f <- directory.listFiles)
    if (f.isFile && ! f.getName.endsWith(".tmp"))
      cache.set(f.getName, f)

  def move(src: File) : Unit = {
    val name = src.getName
    val dest = new File(directory, name)
    assert(src.renameTo(dest))
    cache.set(name, dest)
  }

  def apply(name: String, s3: S3): Future[File] = {
    return cache(name) {
      val tmp = new File(directory, name+".tmp")
      s3.get(name, tmp)
      val cached = new File(directory, name)
      assert(tmp.renameTo(cached))
      cached
    }
  }
}

class CopyS3BlobCache(
  directory: File, cache: FileCache
)(
  implicit ec: ExecutionContext
) extends S3BlobCache(directory, cache) {

  def this(directory: File, capacity_megabytes: Int)
          (implicit ec: ExecutionContext) =
    this(directory, new FileCache(capacity_megabytes))

  // A disk cache to which files must be copies.
  // IOW, source files are on a different file system.

  override def move(src: File) : Unit = {
    val name = src.getName
    val dest = new File(directory, name)
    util.stream_to_file(new FileInputStream(src), dest)
    assert(src.renameTo(dest))
    cache.set(name, dest)
  }
}
