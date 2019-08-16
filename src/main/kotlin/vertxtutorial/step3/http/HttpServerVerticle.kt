package vertxtutorial.step3.http

import com.github.rjeschke.txtmark.Processor
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.launch
import vertxtutorial.step3.database.*
import java.util.*
import io.vertx.config.ConfigRetriever
import io.vertx.kotlin.config.getConfigAwait


class HttpServerVerticle : CoroutineVerticle() {

    private lateinit var dbService: WikiDatabaseService
    private lateinit var templateEngine: FreeMarkerTemplateEngine

    private val CONFIG_HTTP_SERVER_PORT = "http.server.port"
    private val CONFIG_WIKIDB_QUEUE = "wikidb.queue"
    private val LOGGER = LoggerFactory.getLogger(HttpServerVerticle::class.java.simpleName)

    override suspend fun start() {
        val config = ConfigRetriever.create(vertx).getConfigAwait()
        val wikiDbQueue = config.getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue")
        dbService = WikiDatabaseServiceFactory.createProxy(vertx, wikiDbQueue)
        templateEngine = FreeMarkerTemplateEngine.create(vertx)

        val router = Router.router(vertx)
        router.get("/").coroutineHandler(this::indexHandler)
        router.get("/wiki/:page").coroutineHandler(this::pageRenderingHandler)
        router.post().handler(BodyHandler.create())
        router.post("/save").coroutineHandler(this::pageUpdateHandler)
        router.post("/create").handler(this::pageCreateHandler)
        router.post("/delete").coroutineHandler(this::pageDeletionHandler)

        templateEngine = FreeMarkerTemplateEngine.create(vertx)

        val portNumber = config.getInteger(CONFIG_HTTP_SERVER_PORT, 8080)
        vertx.createHttpServer()
                .requestHandler(router)
                .listenAwait(portNumber)
    }

    private suspend fun indexHandler(context: RoutingContext) {
        val res = dbService.fetchAllPagesAwait()
        context.put("title", "Wiki home")
        context.put("pages", res.getList())
        templateEngine.render(context.data(), "templates/index.ftl", { ar ->
            if (ar.succeeded()) {
                context.response().putHeader("Content-Type", "text/html")
                context.response().end(ar.result())
            } else {
                context.fail(ar.cause())
            }
        })
    }

    private suspend fun pageRenderingHandler(context: RoutingContext) {
        val requestedPage = context.request().getParam("page")

        val payLoad = dbService.fetchPageAwait(requestedPage)

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

        templateEngine.render(context.data(), "templates/page.ftl", { ar ->
            if (ar.succeeded()) {
                context.response().putHeader("Content-Type", "text/html")
                context.response().end(ar.result())
            } else {
                context.fail(ar.cause())
            }
        })
    }

    private suspend fun pageUpdateHandler(context: RoutingContext) {
        val title = context.request().getParam("title")
        val markdown = context.request().getParam("markdown")

        if ("yes" == context.request().getParam("newPage")) {
            dbService.createPageAwait(title, markdown)
        } else {
            dbService.savePageAwait(
                    Integer.valueOf(context.request().getParam("id")),
                    markdown)
        }

        context.response().statusCode = 303
        context.response().putHeader("Location", "/wiki/$title")
        context.response().end()
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

    private suspend fun pageDeletionHandler(context: RoutingContext) {
        dbService.deletePageAwait(Integer.valueOf(context.request().getParam("id")))
        context.response().statusCode = 303
        context.response().putHeader("Location", "/")
        context.response().end()
    }

    /**
     * An extension method for simplifying coroutines usage with Vert.x Web routers
     */
    fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Unit) {
        handler { ctx ->
            launch(ctx.vertx().dispatcher()) {
                try {
                    fn(ctx)
                } catch (e: Exception) {
                    ctx.fail(e)
                }
            }
        }
    }


}