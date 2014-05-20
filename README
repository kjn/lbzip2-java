lbzip2-java
===========

http://lbzip2.org/

lbzip2-java is going to be a high-performance Java implementation of
lbzip2 compression and decompression algorithms.

lbzip2-java is free software. You can redistribute and/or modify it
under the terms of Apache License Version 2.0.

The author of lbzip2-java is Mikolaj Izdebski.


Current state
-------------

lbzip2-java is still in initial development.  No release has been made
yet and API has not been defined yet.  Bugs and feature requests can
be submitted either at Github or directly to the author by email.  Any
feedback is also welcome.


Planned features
----------------

In order of descending priority:

  * Low level API for bzip2 compression and decompression, capable of
    creating, accessing or manipulating bz2 streams at level of single
    blocks.

  * High level stream API, implementing OutputStream and InputStream
    filters for compression and decompression (respectively), both
    single- and multi-threaded.

  * Indexer for bz2 files and API providing random access to indexed
    bz2 files (decompression only).  This allows parts of any
    compressed bz2 file to be read and decompressed as long as a tiny
    accompanying index file is available.  Like stream decompressor,
    random-access decompressor is also able to use multiple threads.

  * Command line utility, compatible with lbzip2 interface.
