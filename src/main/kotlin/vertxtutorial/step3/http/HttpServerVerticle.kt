package vertxtutorial.step3.http

import com.github.rjeschke.txtmark.Processor
import com.sun.xml.internal.ws.spi.db.BindingContextFactory.LOGGER
import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine
import vertxtutorial.step3.database.WikiDatabaseService
import vertxtutorial.step3.database.WikiDatabaseServiceFactory
import java.util.*
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.launch

class HttpServerVerticle : CoroutineVerticle() {

    private lateinit var dbService: WikiDatabaseService
    private lateinit var templateEngine: FreeMarkerTemplateEngine
    val CONFIG_HTTP_SERVER_PORT = "http.server.port"
    val CONFIG_WIKIDB_QUEUE = "wikidb.queue"
    private val LOGGER = LoggerFactory.getLogger(HttpServerVerticle::class.java.simpleName)

    override suspend fun start() {
        val wikiDbQueue = config.getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue")
        dbService = WikiDatabaseServiceFactory.createProxy(vertx, wikiDbQueue)
        templateEngine = FreeMarkerTemplateEngine.create(vertx)


        val router = Router.router(vertx)
        router.get("/").handler(this::indexHandler)
        router.get("/").coroutineHandler(this::indexHandler)
        router.get("/wiki/:page").handler(this::pageRenderingHandler)
        router.post().handler(BodyHandler.create())
        router.post("/save").handler(this::pageUpdateHandler)
        router.post("/create").handler(this::pageCreateHandler)
        router.post("/delete").handler(this::pageDeletionHandler)

        templateEngine = FreeMarkerTemplateEngine.create(vertx)

        val portNumber = config.getInteger(CONFIG_HTTP_SERVER_PORT, 8080)
        vertx.createHttpServer()
                .requestHandler(router)
                .list(portNumber) { ar ->
                    if (ar.succeeded()) {
                        LOGGER.info("HTTP server running on port $portNumber")
                        promise.complete()
                    } else {
                        LOGGER.error("Could not start a HTTP server", ar.cause())
                        promise.fail(ar.cause())
                    }
                }
    }

    private fun indexHandler(context: RoutingContext) {
        dbService!!.fetchAllPages(Handler { reply ->
            if (reply.succeeded()) {
                context.put("title", "Wiki home")
                context.put("pages", reply.result().getList())
                templateEngine!!.render(context.data(), "templates/index.ftl", { ar ->
                    if (ar.succeeded()) {
                        context.response().putHeader("Content-Type", "text/html")
                        context.response().end(ar.result())
                    } else {
                        context.fail(ar.cause())
                    }
                })
            } else {
                context.fail(reply.cause())
            }
        })
    }

    private fun pageRenderingHandler(context: RoutingContext) {
        val requestedPage = context.request().getParam("page")
        dbService!!.fetchPage(requestedPage, Handler { reply ->
            if (reply.succeeded()) {

                val payLoad = reply.result()
                val found = payLoad.getBoolean("found")!!
                val rawContent = payLoad.getString("rawContent", "# A new page\n" +
                        "\n" +
                        "Feel-free to write in Markdown!\n")
                context.put("title", requestedPage)
                context.put("id", payLoad.getInteger("id", -1))
                context.put("newPage", if (found) "no" else "yes")
                context.put("rawContent", rawContent)
                context.put("content", Processor.process(rawContent))
                context.put("timestamp", Date().toString())

                templateEngine!!.render(context.data(), "templates/page.ftl", { ar ->
                    if (ar.succeeded()) {
                        context.response().putHeader("Content-Type", "text/html")
                        context.response().end(ar.result())
                    } else {
                        context.fail(ar.cause())
                    }
                })

            } else {
                context.fail(reply.cause())
            }
        })
    }

    private fun pageUpdateHandler(context: RoutingContext) {
        val title = context.request().getParam("title")
        val markdown = context.request().getParam("markdown")
        val handler = Handler<AsyncResult<Void>> { reply ->
            if (reply.succeeded()) {
                context.response().statusCode = 303
                context.response().putHeader("Location", "/wiki/$title")
                context.response().end()
            } else {
                context.fail(reply.cause())
            }
        }
        if ("yes" == context.request().getParam("newPage")) {
            dbService!!.createPage(title, markdown, handler)
        } else {
            dbService!!.savePage(
                    Integer.valueOf(context.request().getParam("id")),
                    markdown,
                    handler
            )
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
        dbService!!.deletePage(Integer.valueOf(context.request().getParam("id")), Handler { reply ->
            if (reply.succeeded()) {
                context.response().statusCode = 303
                context.response().putHeader("Location", "/")
                context.response().end()
            } else {
                context.fail(reply.cause())
            }
        })
    }
}