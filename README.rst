S3 Blob Cache
=============

Storing ZODB blobs in S3 is very attractive based on reliability and
cost.  It's unattractive from a performance point of view, both for
committing transactions and retrieving blobs.

We mitigate the performance problems by:

- committing blobs to disk,

- asynchronously copying blobs to s3 and to local cache, and

- serving blobs from cache, local disk, or s3.

When serving blobs, we look:

- where blobs are committed,

- then in cache, and finally,

- if a blob isn't found in cache or disk, we download it into the
  cache from s3 and then serve it from cache.

Blobs are served via HTTP, rather than from the ZEO server.  This
requires a small change to clients, but frees the Python ZEO server
from the load of downloading blobs. This can provide significant
performance improvements when the ZEO server is heavily loaded.
(In the furture, in a ZEO server no-longer limited by a global
interpreter lock, we might move blob serving back into the ZEO
server.)

Design
======

Blob server:

- Disk cache, based on spray.caching

- Watcher actor(s) copy blobs to S3 then move them to cache

- HTTP server (spray.routing)

ZEO server:

- flat blob layout, with a single directory containing blob files
  (plus a temporary directory, as usual).

- Add hook to override blob garbage disposal, as garbage blobs will
  need to be removed from S3, rather than the local file system.
  (Or, in some cases, in addition to the local file system.)

ZEO client:

- Client option to load blobs from an HTTP server.
