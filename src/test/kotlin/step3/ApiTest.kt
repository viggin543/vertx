package step3

import io.vertx.core.DeploymentOptions
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import vertxtutorial.step3.database.WikiDatabaseVerticle
import vertxtutorial.step3.http.HttpServerVerticle
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.core.Promise.promise
import io.vertx.core.json.JsonArray
import io.vertx.ext.unit.Async
import io.vertx.ext.web.client.HttpResponse
import org.junit.Test
import vertxtutorial.config.DatabaseConstants.Companion.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE
import vertxtutorial.config.DatabaseConstants.Companion.CONFIG_WIKIDB_JDBC_URL


@RunWith(VertxUnitRunner::class)
class ApiTest {

    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient

    @Before
    fun prepare(context: TestContext) {
        vertx = Vertx.vertx()

        val dbConf = JsonObject()
                .put(CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
                .put(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4)

        runBlocking {
            val client = dbClientForTest(dbConf, vertx)
            vertx.deployVerticle(WikiDatabaseVerticle(client),
                    DeploymentOptions().setConfig(dbConf), context.asyncAssertSuccess())
        }

        vertx.deployVerticle(HttpServerVerticle(), context.asyncAssertSuccess())

        webClient = WebClient.create(vertx, WebClientOptions()
                .setDefaultHost("localhost")
                .setDefaultPort(8080))
    }

    @After
    fun finish(context: TestContext) {
        vertx.close(context.asyncAssertSuccess())
    }


    @Test
    fun play_with_api(context: TestContext) {
        val async = context.async()

        val page = JsonObject()
                .put("name", "Sample")
                .put("markdown", "# A page")


        val postPagePromise = promise<HttpResponse<JsonObject>>()
        webClient.post("/api/pages")
                .`as`(BodyCodec.jsonObject())
                .sendJsonObject(page, postPagePromise)

        val getPageFuture = postPagePromise.future().compose<HttpResponse<JsonObject>> {
            val promise = promise<HttpResponse<JsonObject>>()
            webClient.get("/api/pages")
                    .`as`(BodyCodec.jsonObject())
                    .send(promise)
            promise.future()
        }

        val updatePageFuture = getPageFuture.compose { resp ->
            val array = resp.body().getJsonArray("pages")
            context.assertEquals(1, array.size())
            context.assertEquals(0, array.getJsonObject(0).getInteger("id"))
            val promise = promise<HttpResponse<JsonObject>>()
            val data = JsonObject()
                    .put("id", 0)
                    .put("markdown", "Oh Yeah!")
            webClient.put("/api/pages/0")
                    .`as`(BodyCodec.jsonObject())
                    .sendJsonObject(data, promise)
            promise.future()
        }

        val deletePageFuture = updatePageFuture.compose { resp ->
            context.assertTrue(resp.body().getBoolean("success"))
            val promise = promise<HttpResponse<JsonObject>>()
            webClient.delete("/api/pages/0")
                    .`as`(BodyCodec.jsonObject())
                    .send(promise)
            promise.future()
        }

        deletePageFuture.setHandler { ar ->
            if (ar.succeeded()) {
                context.assertTrue(ar.result().body().getBoolean("success"))
                async.complete()
            } else {
                context.fail(ar.cause())
            }
        }

        async.awaitSuccess(5000)
    }
}