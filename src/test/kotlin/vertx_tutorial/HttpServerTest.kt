package vertx_tutorial

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import kotlin.test.Test
import vertx_tutorial.step_0.HttpServer
import java.net.ServerSocket




@RunWith(VertxUnitRunner::class)
class HttpServerTest {

    private var vertx: Vertx? = null
    private var port: Int = 0

    @Before
    fun setUp(context: TestContext) {
        vertx = Vertx.vertx()
        val socket = ServerSocket(0)
        port = socket.localPort

        socket.close()
        vertx!!.deployVerticle(HttpServer::class.java.name,
                DeploymentOptions()
                        .setConfig(JsonObject().put("http.port", port)),
                context.asyncAssertSuccess<String>()
        )
    }

    @After
    fun tearDown(context: TestContext) {
        vertx!!.close(context.asyncAssertSuccess())
    }

    @Test
    fun testMyApplication(context: TestContext) {
        val async = context.async()


        vertx!!.createHttpClient().getNow(port, "localhost", "/") {
            it.handler { body ->
                context.assertTrue(body.toString().contains("Hello"))
                async.complete()
            }
        }
    }
}