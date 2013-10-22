package com.zope.s3blobserver

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import com.googlecode.concurrentlinkedhashmap.EvictionListener
import com.googlecode.concurrentlinkedhashmap.EntryWeigher
import java.io.File
import scala.concurrent.{ Promise, ExecutionContext, Future }
import scala.util.Success
import spray.caching.Cache

/**
 * Based on the SimpleLruCache from spray.cache
 *  
 * A thread-safe implementation of [[spray.caching.cache]].  The cache
 * has a defined maximum number of megabytes it can store. After the
 * maximum capacity is reached new entries cause old ones to be
 * evicted in a least-recently-used manner.
 */
final class FileCache(
  val maxCapacity: Int
) extends Cache[File] {

  require(maxCapacity >= 0,
          "maxCapacity must not be negative")

  val store = (new ConcurrentLinkedHashMap.Builder[Any, Future[File]]
    .maximumWeightedCapacity(maxCapacity * 128)
    .listener(evicted)
    .weigher(file_size_weigher)
    .build()
  )

  def get(key: Any) = Option(store.get(key))

  def set(key: Any, value: File)(implicit ec: ExecutionContext): Unit = {
    this(key) { value }
  }

  def apply(key: Any, genValue: () ⇒ Future[File])(
    implicit ec: ExecutionContext
  ): Future[File] = {

    val promise = Promise[File]()
    store.putIfAbsent(key, promise.future) match {
      case null ⇒
        val future = genValue()
        future.onComplete { value ⇒
          promise.complete(value)
          // in case of exceptions we remove the cache entry (i.e. try
          // again later)
          if (value.isFailure) store.remove(key, promise)
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

  def count = store.size
  def size = store.weightedSize * 8192
}

object evicted extends EvictionListener[Any, Future[File]] {
  override def onEviction(k: Any, v: Future[File]) : Unit = {
    if (v.isCompleted) 
      v.value map {
        _ match {
          case Success(f) => f.delete
          case _ =>
        }
      }
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
