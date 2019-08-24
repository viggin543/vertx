package vertxtutorial.step3.http

import com.github.rjeschke.txtmark.Processor
import io.vertx.config.ConfigRetriever
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine
import io.vertx.kotlin.config.getConfigAwait
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.ext.auth.isAuthorizedAwait
import io.vertx.kotlin.ext.web.client.sendJsonObjectAwait
import io.vertx.kotlin.ext.web.common.template.renderAwait
import vertxtutorial.step3.database.*
import java.util.*


class HttpServerVerticle : CoroutineVerticle(), CoroutineHandler {

    private lateinit var dbService: WikiDatabaseService
    private lateinit var templateEngine: FreeMarkerTemplateEngine
    private lateinit var webClient: WebClient

    private val CONFIG_HTTP_SERVER_PORT = "http.server.port"
    private val CONFIG_WIKIDB_QUEUE = "wikidb.queue"
    private val LOGGER = LoggerFactory.getLogger(HttpServerVerticle::class.java.simpleName)


    override suspend fun start() {
        val config = ConfigRetriever.create(vertx).getConfigAwait()
        dbService = WikiDatabaseServiceFactory.createProxy(vertx,
                config.getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue")
        )
        templateEngine = FreeMarkerTemplateEngine.create(vertx)
        webClient = WebClient.create(vertx, WebClientOptions()
                .setSsl(true)
                .setUserAgent("vert-x3"))

        val router = Router.router(vertx).apply {
            val self = this@HttpServerVerticle

            /* question, if we deploy two of these verticles. do we create two auth handlers ? */
            setupAuthentication(vertx, templateEngine)

            get("/").coroutineHandler(self::indexHandler)
            get("/wiki/:page").coroutineHandler(self::pageRenderingHandler)
            post().handler(BodyHandler.create())
            post("/action/save").coroutineHandler(self::pageUpdateHandler)
            post("/action/create").handler(self::pageCreateHandler)
            get("/action/backup").coroutineHandler(self::backupHandler)
            post("/action/delete").coroutineHandler(self::pageDeletionHandler)
        }

        with(Router.router(vertx)) {
            val api = Api(dbService)
            get("/pages").coroutineHandler(api::apiRoot)
            get("/pages/:id").coroutineHandler(api::apiGetPage)
            post().handler(BodyHandler.create())
            post("/pages").coroutineHandler(api::apiCreatePage)
            put().handler(BodyHandler.create())
            put("/pages/:id").coroutineHandler(api::apiUpdatePage)
            delete("/pages/:id").coroutineHandler(api::apiDeletePage)
            router.mountSubRouter("/api", this)
        }

        vertx.createHttpServer()
                .requestHandler(router)
                .listenAwait(config.getInteger(CONFIG_HTTP_SERVER_PORT, 8080))
    }



    private suspend fun backupHandler(context: RoutingContext) {
        val pagesData = dbService.fetchAllPagesDataAwait()
        val payload = JsonObject()
                .put("files", JsonArray().apply {
                    pagesData.forEach { page ->
                        add(JsonObject().apply {
                            put("name", page.getString("NAME"))
                            put("content", page.getString("CONTENT"))
                        })
                    }
                })
                .put("language", "plaintext")
                .put("title", "vertx-wiki-backup")
                .put("public", true)

        with(webClient.post(443, "snippets.glot.io", "/snippets")
                .putHeader("Content-Type", "application/json")
                .`as`(BodyCodec.jsonObject()) //parses response as json
                .sendJsonObjectAwait(payload)) {
            when {
                statusCode() == 200 -> {
                    val url = "https://glot.io/snippets/" + body().getString("id")
                    context.put("backup_gist_url", url)
                    indexHandler(context)
                }
                else -> {
                    LOGGER.error(StringBuilder()
                            .append("Could not backup the wiki: ")
                            .append(statusMessage())
                            .append(System.getProperty("line.separator"))
                            .append(body()?.encodePrettily() ?: "").toString())
                    context.fail(502)
                }
            }
        }


    }

    private suspend fun indexHandler(context: RoutingContext) {
        val res = dbService.fetchAllPagesAwait()
        val canCreatePage = context.user().isAuthorizedAwait("create")
        context.put("title", "Wiki home")
        context.put("pages", res.list)
        context.put("canCreatePage", canCreatePage)
        context.put("username", context.user().principal().getString("username"))
        val page = templateEngine.renderAwait(context.data(), "templates/index.ftl")
        context.response().putHeader("Content-Type", "text/html")
        context.response().end(page)
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

        templateEngine.render(context.data(), "templates/page.ftl") { ar ->
            if (ar.succeeded()) {
                context.response().putHeader("Content-Type", "text/html")
                context.response().end(ar.result())
            } else {
                context.fail(ar.cause())
            }
        }
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
        val canDelete = context.user().isAuthorizedAwait("delete")
        if(canDelete) {
            dbService.deletePageAwait(Integer.valueOf(context.request().getParam("id")))
            context.response()
                    .setStatusCode(303)
                    .putHeader("Location", "/")
                    .end()
        } else {
            context.response().setStatusCode(403).end()
        }

    }

}