# Description of the issue

The following code is very likely to create an `OutOfMemoryError`:

```kotlin
val docs = mongoClient.findBatchWithOptions(
    "collection",
    JsonObject(), // filter
    findOptionsOf(batchSize = 512)
).toReceiveChannel(vertx)

for (doc in docs) {
    // perform an asynchronous operation
}
```

Note that 512 element do fit into memory without any problem.

# How to reproduce

My setup:
1. OpenJDK 11
2. Maven 3.8.1

```python
MAVEN_OPTS=-Xmx128m mvn clean package exec:java
```

Note: running the program in IntelliJ seems to be problematic because of AspectJ (?), so remove it from the pom if you want to to so.

# Explanation of the reproducer program

The reproducer code will:
1. create a MongoDB container using Test Containers
2. create a `vertxTest` database
3. insertion step: insert 100,000 documents into a `documents` collection
   1. first 10,000 documents have a specific and correct "format"
   2. other documents have another and incorrect "format"
4. processing step: loop on all documents using a find `findBatchWithOptions` and `toReceiveChannel`
   1. if current document has the correct "format" then do nothing
   2. if current document has the incorrect "format" then rewrite it, add it to a bulk, and run a bulk update from time to time

It means that the beginning of the loop will be very fast (first documents are correct) then,
as soon as incorrect documents appear, there will be some asynchronous bulk updates.

The reproducer:
1. uses AspectJ to trace `io.vertx.ext.mongo.impl.PublisherAdapter#handleIn` and `io.vertx.ext.mongo.impl.PublisherAdapter#requestMore` method calls
2. uses java.lang.reflect tricks to trace capacity changes of the underlying `io.vertx.core.streams.impl.InboundBuffer` of the `io.vertx.ext.mongo.impl.PublisherAdapter`
3. uses AspectJ to detect if a `drain()` occurs in the `InboundBuffer` of the `PublisherAdapter`
4. uses AspectJ to detect if `pause()` or `resume()` occur in the `PublisherAdapter`


# What is observed

Here are the traces of the program:

Traces of the insertion step:
```
MongoDB test container created.
inserting test documents...
1000 documents inserted...
2000 documents inserted...
[...]
99000 documents inserted...
100000 documents inserted...
test documents insertion complete.
```
Then traces of the processing step (test program only shows those of the first 50 documents):

```
number of documents in collection: 100000
updating test documents...
[PublisherAdapter] InboundBuffer pause
[PublisherAdapter] requestMore called
[PublisherAdapter] handleIn called
1 documents processed
queue capacity changed: 16
[PublisherAdapter] requestMore called
[PublisherAdapter] handleIn called
2 documents processed
[PublisherAdapter] requestMore called
[PublisherAdapter] handleIn called
3 documents processed
[PublisherAdapter] requestMore called
[PublisherAdapter] handleIn called
4 documents processed
[PublisherAdapter] requestMore called
[PublisherAdapter] handleIn called
5 documents processed
[PublisherAdapter] requestMore called
[PublisherAdapter] handleIn called
6 documents processed
[PublisherAdapter] requestMore called
[PublisherAdapter] handleIn called
7 documents processed
[PublisherAdapter] requestMore called
[PublisherAdapter] handleIn called
8 documents processed
[PublisherAdapter] requestMore called
[PublisherAdapter] handleIn called
9 documents processed
[PublisherAdapter] requestMore called
[PublisherAdapter] handleIn called
10 documents processed
[...]
```
We can see that after each item added to the stream (`handleIn`), 
the code calls the `requestMore` method that will request a new batch of 512 documents.

Note the that `requestMode` method eventually requests `batchSize` elements from the publisher:
```java
  private void requestMore() {
    Subscription s;
    synchronized (this) {
      if (state == State.STOPPED) {
        return;
      }
      s = this.subscription;
      remainingItemsInPublisherBatch = batchSize;
    }
    s.request(batchSize);
  }

```

Then it becomes less fine-grained:
```100 documents processed
200 documents processed
300 documents processed
400 documents processed
500 documents processed
600 documents processed
700 documents processed
800 documents processed
900 documents processed
1000 documents processed
1100 documents processed
[...]
10200 documents processed
10300 documents processed
10400 documents processed
10500 documents processed
```

Then bulk operations start, and we can see that the capacity of the `InboundBuffer` starts growing (infinitely) and that the drain is called (but not pause):

```
10500 documents processed
writing bulk of docs with categories...
bulk written
[PublisherAdapter] InboundBuffer fetch drained!!
queue capacity changed: 13528
10600 documents processed
10700 documents processed
10800 documents processed
10900 documents processed
11000 documents processed
writing bulk of docs with categories...
bulk written
[PublisherAdapter] InboundBuffer fetch drained!!
queue capacity changed: 20292
11100 documents processed
11200 documents processed
11300 documents processed
11400 documents processed
11500 documents processed
writing bulk of docs with categories...
bulk written
[PublisherAdapter] InboundBuffer fetch drained!!
queue capacity changed: 30438
11600 documents processed
11700 documents processed
11800 documents processed
11900 documents processed
12000 documents processed
writing bulk of docs with categories...
bulk written
[PublisherAdapter] InboundBuffer fetch drained!!
12100 documents processed
12200 documents processed
12300 documents processed
12400 documents processed
12500 documents processed
writing bulk of docs with categories...
bulk written
[PublisherAdapter] InboundBuffer fetch drained!!
12600 documents processed
12700 documents processed
12800 documents processed
12900 documents processed
13000 documents processed
writing bulk of docs with categories...
bulk written
[PublisherAdapter] InboundBuffer fetch drained!!
queue capacity changed: 45657
13100 documents processed
13200 documents processed
13300 documents processed
13400 documents processed
13500 documents processed
writing bulk of docs with categories...
bulk written
[PublisherAdapter] InboundBuffer fetch drained!!
13600 documents processed
13700 documents processed
13800 documents processed
13900 documents processed
14000 documents processed
14100 documents processed
writing bulk of docs with categories...
bulk written
[PublisherAdapter] InboundBuffer fetch drained!!
14200 documents processed
14300 documents processed
14400 documents processed
14500 documents processed
14600 documents processed
writing bulk of docs with categories...
```
until:
```
java.lang.OutOfMemoryError: Java heap space
    at io.vertx.core.impl.btc.BlockedThreadChecker$1.run (BlockedThreadChecker.java:55)
    at java.util.TimerThread.mainLoop (Timer.java:556)
    at java.util.TimerThread.run (Timer.java:506)
java.lang.OutOfMemoryError: Java heap space
```

My understanding of the issue is:

As long as the processing loop does nothing (first elements), 
the loop "keeps the control" of the eventbus thread and documents are gently passing 
from the MongoDB publisher to the consuming loop without being enqueued in the `InboundBuffer`. 

As soon as the processing loop starts to perform asynchronous operations:
1. the high number of `requestMore` orders are being executed and start to enqueue a lot of objects in the `InboundBuffer`?
2. or the `drain` puts the process in "flowing mode"? 

# Potential fix?

I have the impression the issue is related to the `inFlight` field of `PublisherAdapter`.
It should somehow keep track of the `requestMore` calls and be aware of `batchSize`.

I tried to do so, it seems to be working, but as I don't understand the subtilities of ReadStream, Publisher, InboundQueue etc... 
my modification might be crappy.

# My understanding of Kotlin-specific additions

Kotlin adds the `toReceiveChannel` in the picture (compared to pure Java code). 

If I understand correctly, that channel will issue the following sequence of calls on the stream:
1. pause()
2. fetch(1)
3. then event handler will 
   1. send the event to the channel
   2. fetch(1)
