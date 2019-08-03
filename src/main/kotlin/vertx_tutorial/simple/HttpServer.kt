package vertx_tutorial.simple

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import vertx_tutorial.logger


class HttpServer : AbstractVerticle() {

    override fun start(startFuture: Future<Void>) {

        val router = Router.router(vertx)

        router.route("/").handler {
            it.response()
                    .putHeader("content-type", "text/html")
                    .end("<h1>Hello from my first Vert.x 3 application</h1>")
        }
        router.route("/assets/*").handler(StaticHandler.create("assets"))
        val port = getPort()
        vertx
                .createHttpServer()
                .requestHandler(router)
                .listen(port!!) {
                    when {
                        it.succeeded() -> startFuture.complete()
                        else -> startFuture.fail(it.cause())
                    }
                }
    }

    private fun getPort(): Int? {
        val port = config().getInteger("http.port", 8080)
        logger.info("vertex http server listening on $port")
        return port
    }

    @Throws(Exception::class)
    override fun stop() {
        logger.info("http verticle stopped!")
    }



}