package vertxtutorial.step_2

import com.github.rjeschke.txtmark.Processor
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine
import vertxtutorial.config.DatabaseConstants.Companion.CONFIG_WIKIDB_QUEUE
import java.util.*


class HttpVerticle : AbstractVerticle() {
    private val LOGGER = LoggerFactory.getLogger(HttpVerticle::class.simpleName)

    private lateinit var wikiDbQueue : String
    private val port = "http.server.port"

    private lateinit var templateEngine: FreeMarkerTemplateEngine


    @Throws(Exception::class)
    override fun start(promise: Promise<Void>) {

        wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue")

        val router = Router.router(vertx)
        router.get("/").handler(this::indexHandler)
        router.get("/wiki/:page").handler(this::pageRenderingHandler)
        router.post().handler(BodyHandler.create())
        router.post("/save").handler(this::pageUpdateHandler)
        router.post("/create").handler(this::pageCreateHandler)
        router.post("/delete").handler(this::pageDeletionHandler)

        templateEngine = FreeMarkerTemplateEngine.create(vertx)

        val portNumber = config().getInteger(port, 8080)!!
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(portNumber) { ar ->
                    when {
                        ar.succeeded() -> {
                            LOGGER.info("HTTP server running on port $portNumber")
                            promise.complete()
                        }
                        else -> {
                            LOGGER.error("Could not start a HTTP server", ar.cause())
                            promise.fail(ar.cause())
                        }
                    }
                }
    }

    private fun indexHandler(context: RoutingContext) {

        val options = DeliveryOptions().addHeader("action", "all-pages")

        vertx.eventBus().request<Any>(wikiDbQueue, JsonObject(), options) { reply ->
            if (reply.succeeded()) {
                val body = reply.result().body() as JsonObject
                context.put("title", "Wiki home")
                context.put("pages", body.getJsonArray("pages").list)
                templateEngine!!.render(context.data(), "templates/index.ftl") { ar ->
                    when {
                        ar.succeeded() -> {
                            context.response().putHeader("Content-Type", "text/html")
                            context.response().end(ar.result())
                        }
                        else -> context.fail(ar.cause())
                    }
                }
            } else {
                context.fail(reply.cause())
            }
        }
    }

    private fun pageRenderingHandler(context: RoutingContext) {

        val requestedPage = context.request().getParam("page")
        val request = JsonObject().put("page", requestedPage)

        val options = DeliveryOptions().addHeader("action", "get-page")
        vertx.eventBus().request<Any>(wikiDbQueue, request, options) { reply ->

            when {
                reply.succeeded() -> {
                    val body = reply.result().body() as JsonObject

                    val found = body.getBoolean("found")!!
                    val rawContent = body.getString("rawContent", "# A new page\n" +
                            "\n" +
                            "Feel-free to write in Markdown!\n")
                    context.put("title", requestedPage)
                    context.put("id", body.getInteger("id", -1))
                    context.put("newPage", if (found) "no" else "yes")
                    context.put("rawContent", rawContent)
                    context.put("content", Processor.process(rawContent))
                    context.put("timestamp", Date().toString())

                    templateEngine!!.render(context.data(), "templates/page.ftl") { ar ->
                        when {
                            ar.succeeded() -> {
                                context.response().putHeader("Content-Type", "text/html")
                                context.response().end(ar.result())
                            }
                            else -> context.fail(ar.cause())
                        }
                    }

                }
                else -> context.fail(reply.cause())
            }
        }
    }

    private fun pageUpdateHandler(context: RoutingContext) {

        val title = context.request().getParam("title")
        val request = JsonObject()
                .put("id", context.request().getParam("id"))
                .put("title", title)
                .put("markdown", context.request().getParam("markdown"))

        val options = DeliveryOptions()
        when {
            "yes" == context.request().getParam("newPage") -> options.addHeader("action", "create-page")
            else -> options.addHeader("action", "save-page")
        }

        vertx.eventBus().request<Any>(wikiDbQueue, request, options) { reply ->
            when {
                reply.succeeded() -> {
                    context.response().statusCode = 303
                    context.response().putHeader("Location", "/wiki/" + title)
                    context.response().end()
                }
                else -> context.fail(reply.cause())
            }
        }
    }

    private fun pageCreateHandler(context: RoutingContext) {
        val pageName = context.request().getParam("name")
        var location = "/wiki/$pageName"
        if (pageName?.isEmpty() == true) {
            location = "/"
        }
        context.response().statusCode = 303
        context.response().putHeader("Location", location)
        context.response().end()
    }

    private fun pageDeletionHandler(context: RoutingContext) {
        val id = context.request().getParam("id")
        val request = JsonObject().put("id", id)
        val options = DeliveryOptions().addHeader("action", "delete-page")
        vertx.eventBus().request<Any>(wikiDbQueue, request, options) { reply ->
            when {
                reply.succeeded() -> {
                    context.response().statusCode = 303
                    context.response().putHeader("Location", "/")
                    context.response().end()
                }
                else -> context.fail(reply.cause())
            }
        }
    }
}
