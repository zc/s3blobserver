package com.zope.s3blobserver

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import com.googlecode.concurrentlinkedhashmap.EvictionListener
import com.googlecode.concurrentlinkedhashmap.EntryWeigher
import java.io.File
import scala.concurrent.{ Promise, ExecutionContext, Future }
import scala.util.Success

/**
 * Based on the SimpleLruCache from spray.cache
 *  
 * A thread-safe implementation of [[spray.caching.cache]].  The cache
 * has a defined maximum number of megabytes it can store. After the
 * maximum capacity is reached new entries cause old ones to be
 * evicted in a least-recently-used manner.
 *
 * Had to fork because the original, spray.caching.LruCache is final. :(
 * 
 * Also the semantics of ``cache(key) func`` are different.  This
 * implementation wraps the function in a future, so it runs in a
 * future. The original didn't.
 */
class FileCache(val maxCapacity: Int)(implicit ec: ExecutionContext) {

  require(maxCapacity >= 0,
          "maxCapacity must not be negative")

  val store = (new ConcurrentLinkedHashMap.Builder[Any, Future[File]]
    .maximumWeightedCapacity(maxCapacity * 128) // *(1<<20)/8192
    .listener(new FileEvictionListener)
    .weigher(file_size_weigher)
    .build()
  )

  def get(key: Any) = Option(store.get(key))

  def set(key: Any, value: File): Unit = {
    this(key) { value }
  }

  def apply(key: Any)(get_file: ⇒ File): Future[File] = {

    val promise = Promise[File]()
    store.putIfAbsent(key, promise.future) match {
      case null ⇒
        val future = Future { get_file }
        future.onComplete {
          value ⇒
          promise.complete(value)
          // in case of exceptions we remove the cache entry (i.e. try
          // again later)
          if (value.isFailure)
            store.remove(key, promise)
          else
            // inform the store that the weaight has changed, cuz we
            // can actually compute a size now.
            store.replace(key, promise.future)
        }
        future
      case existingFuture ⇒ existingFuture
    }
  }

  def remove(key: Any) = Option(store.remove(key))

  def clear() = store.clear()

  def size = store.size

  def bytes = store.weightedSize * 8192
}

class FileEvictionListener(
  implicit ec: ExecutionContext
) extends EvictionListener[Any, Future[File]] {

  override def onEviction(k: Any, v: Future[File]) : Unit = {
    v map { file => file.delete }
  }
}

object file_size_weigher extends EntryWeigher[Any, Future[File]] {
  override def weightOf(k: Any, v: Future[File]) : Int = {
    v.value map {
      _ match {
        case Success(f) => return ((f.length / 8192) max 1).asInstanceOf[Int]
        case _ => 1
      }
    }
    1
  }
}
