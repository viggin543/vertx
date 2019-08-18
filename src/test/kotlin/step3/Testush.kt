package step3

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.awaitResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vertxtutorial.step3.database.*


@RunWith(VertxUnitRunner::class)
class Testush {

    private lateinit var vertx: Vertx
    private lateinit var service: WikiDatabaseService


    @Before
    fun prepare(context: TestContext) {

        vertx = Vertx.vertx()

        val conf = JsonObject()
                .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:db?shutdown=true")
                .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 30)


        runBlocking {
            val client = dbClientForTest(conf, vertx)
            try {
                vertx.deployVerticle(WikiDatabaseVerticle(client), DeploymentOptions().setConfig(conf))
                service = WikiDatabaseServiceFactory.createProxy(vertx, WikiDatabaseVerticle.CONFIG_WIKIDB_QUEUE)
            } catch (e: Throwable) {
                e.printStackTrace()
                context.fail(e)
            }
            println("finished test setup")
        }
    }


    @Test
    fun crud_operations(context: TestContext) {
        runBlocking {
            println("starting test with in mem db")
            service.createPageAwait("Test", "Some content")
            val page1 = service.fetchPageAwait("Test")
            context.assertTrue(page1.getBoolean("found"))
            context.assertTrue(page1.containsKey("id"))
            context.assertEquals("Some content", page1.getString("rawContent"))

            service.savePageAwait(page1.getInteger("id"), "Yo!")
            val array1 = service.fetchAllPagesAwait()
            context.assertEquals(1, array1.size())
            val json2 = service.fetchPageAwait("Test")

            context.assertEquals("Yo!", json2.getString("rawContent"))

            service.deletePageAwait(page1.getInteger("id"))
            service.fetchAllPagesAwait()
        }
    }

    @After
    fun finish(context: TestContext) {
        vertx.close(context.asyncAssertSuccess())
    }

    @Test
    fun async_behavior(context: TestContext) {
        val vertx = Vertx.vertx()
        context.assertEquals("foo", "foo")
        val a1 = context.async()
        val a2 = context.async(3)
        vertx.setTimer(100) { n -> println(n);a1.complete() }
        vertx.setPeriodic(100) { n -> println(n);a2.countDown() }
    }

    @Test
    fun start_http_server(context: TestContext) {

        runBlocking {
            awaitResult<HttpServer> {
                vertx.createHttpServer().requestHandler { req -> req.response().putHeader("Content-Type", "text/plain").end("Ok") }
                    .listen(8080,it)
            }
            val webClient: WebClient = WebClient.create(vertx)
            val response: HttpResponse<Buffer> = awaitResult {
                webClient.get(8080, "localhost", "/").send(it)
            }
            context.assertTrue(response.headers().contains("Content-Type"))
            context.assertEquals("text/plain", response.getHeader("Content-Type"))
            context.assertEquals("Ok", response.body().toString())
            webClient.close()
        }
    }
}



