package org.bfreuden

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.streams.impl.InboundBuffer
import io.vertx.ext.mongo.BulkOperation
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.mongo.impl.PublisherAdapter
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.toReceiveChannel
import io.vertx.kotlin.ext.mongo.findOptionsOf
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import java.util.*

const val MONGODB_PORT = 27018
const val USE_TEST_CONTAINERS = true
const val DATABASE = "vertxTest"

fun main() = runBlocking<Unit> {

    var mongoDBContainer: MongoDBContainer? = null
    if (USE_TEST_CONTAINERS) {
        println("creating MongoDB test container...")
        mongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:4.2.1"))
        mongoDBContainer.portBindings = listOf("$MONGODB_PORT:27017")
        mongoDBContainer.start()
        println("MongoDB test container created.")
    }
    val vertx = Vertx.vertx()
    try {
        // (vertx mongodb client does not work well outside a verticle)
        // insert sample data into MongoDB
        vertx.deployVerticle(MongoDBInitTestDataVerticle()).await()
        // run the test
        vertx.deployVerticle(MongoDBTestVerticle()).await()
    } catch (t: Throwable) {
        t.printStackTrace()
    } finally {
        vertx.close()
        mongoDBContainer?.stop()
    }
}

private fun createMongoDBClient(vertx: Vertx) = MongoClient.createShared(
    vertx, json { obj("host" to "localhost", "port" to MONGODB_PORT, "db_name" to DATABASE) }, DATABASE
)


class MongoDBInitTestDataVerticle: CoroutineVerticle() {

    override suspend fun start() {
        val client = createMongoDBClient(vertx)
        val bulk = mutableListOf<BulkOperation>()
        val nbDocuments = 100_000
        val bulkSize = 100
        var inserted = 0
        println("inserting test documents...")
        // will insert 100k documents
        // 1000 first ones will be slightly different from the others (categories will have an identifier field)
        // the purpose of the test will be to modify the others to have the same format as the first ones.
        repeat(nbDocuments / bulkSize) {
            repeat(bulkSize) {
                val docID = UUID.randomUUID().toString()
                bulk.add(BulkOperation.createInsert(
                    json { obj(
                        "identifier" to docID,
                        "title" to docID,
                        "metadata" to obj(),
                        "createdBy" to "admin",
                        "createdAt" to "2022-03-18T12:15:35.043Z",
                        "modifiedAt" to "2022-03-18T12:15:35.043Z",
                        "text" to "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed non risus. Suspendisse lectus tortor, dignissim sit amet, adipiscing nec, ultricies sed, dolor. Cras elementum ultrices diam.",
                        "categories" to array(
                            obj(
                                "label" to "positive",
                                "score" to 1,
                                "createdBy" to "admin",
                                "createdAt" to "2022-03-18T12:15:35.043Z",
                                "modifiedAt" to "2022-03-18T12:15:35.043Z",
                                "status" to "OK",
                            ).apply {
                                // 10% first documents have categories with an identifier field
                                if (inserted < (nbDocuments / 10))
                                    put("identifier", UUID.randomUUID().toString())

                            }
                        )
                    )}
                ))
            }
            client.bulkWrite("documents", bulk).await()
            bulk.clear()
            inserted += bulkSize
            if (inserted % 1000 == 0)
                println("$inserted documents inserted...")
        }
        println("test documents insertion complete.")
    }

}

class MongoDBTestVerticle: CoroutineVerticle() {

    override suspend fun start() {
        val internalQueueField = PublisherAdapter::class.java.getDeclaredField("internalQueue")
        internalQueueField.isAccessible = true
        val pendingField = InboundBuffer::class.java.getDeclaredField("pending")
        pendingField.isAccessible = true
        val elementsField = java.util.ArrayDeque::class.java.getDeclaredField("elements")
        elementsField.isAccessible = true
        var currentCapacity = 0
        val client = createMongoDBClient(vertx)
        val nbDocuments = client.count("documents", JsonObject()).await()
        println("number of documents in collection: $nbDocuments")
        println("updating test documents...")
        val suitableBatchSize = 512
        val publisher = client.findBatchWithOptions(
            "documents",
            json { obj("categories" to obj("\$exists" to true, "\$ne" to array())) },
            findOptionsOf(batchSize = suitableBatchSize, fields = json { obj("categories" to 1)})
        )
        val docs = publisher.toReceiveChannel(vertx)
        val bulk = mutableListOf<BulkOperation>()
        var nbProcessed = 0
        for (doc in docs) {
            nbProcessed++
            if (nbProcessed <= 50)
                println("$nbProcessed documents processed")
            else if (nbProcessed % 100 == 0)
                println("$nbProcessed documents processed")
            val buffer = internalQueueField.get(publisher) as InboundBuffer<*>
            val deque = pendingField.get(buffer) as java.util.ArrayDeque<*>
            val array = elementsField.get(deque) as Array<*>
            if (array.size != currentCapacity) {
                currentCapacity = array.size
                println("queue capacity changed: $currentCapacity")
            }
            val categories = doc.getJsonArray("categories")
            doc.remove("categories")
            var updateDoc = false
            for (i in 0 until categories.size()) {
                val category = categories.getJsonObject(i)
                if (category.getString("identifier") == null) {
                    category.put("identifier", UUID.randomUUID().toString())
                    updateDoc = true
                }
            }
            if (updateDoc) {
                bulk.add(BulkOperation.createUpdate(doc, json { obj("\$set" to obj("categories" to categories)) }))
                if (bulk.size > suitableBatchSize) {
                    println("writing bulk of docs with categories...")
                    client.bulkWrite("documents", bulk).await()
                    bulk.clear()
                    println("bulk written")
                }
            }

        }
        if (bulk.isNotEmpty()) {
            println("writing bulk of docs with categories...")
            client.bulkWrite("documents", bulk).await()
            bulk.clear()
            println("bulk written")
        }
        println("test documents update complete.")

    }


}


