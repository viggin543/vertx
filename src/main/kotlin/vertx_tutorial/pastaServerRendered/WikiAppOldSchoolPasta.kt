package vertx_tutorial.pastaServerRendered

import com.github.rjeschke.txtmark.Processor
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine
import java.util.*


class WikiAppOldSchoolPasta : AbstractVerticle() {
    val SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)"
    val SQL_GET_PAGE = "select Id, Content from Pages where Name = ?"
    val SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)"
    val SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?"
    val SQL_ALL_PAGES = "select Name from Pages"
    val SQL_DELETE_PAGE = "delete from Pages where Id = ?"

    private var dbClient: JDBCClient? = null
    private var templateEngine: FreeMarkerTemplateEngine? = null
    private val LOGGER = LoggerFactory.getLogger(WikiAppOldSchoolPasta::class.java.simpleName)

    override fun start(startFuture: Future<Void>) {
        LOGGER.info("verticle sarting")
        LOGGER.info(prepareDatabase().isComplete)
        prepareDatabase().recover {
            LOGGER.error(it.message)
            Promise.promise<Void>().future()
        }
        prepareDatabase().compose {
            LOGGER.info("b4 startHttpServer")
            startHttpServer()
        }.setHandler(startFuture)

    }

    private fun prepareDatabase(): Future<Void> {
        val promise1: Promise<Void> = Promise.promise()
        dbClient = JDBCClient.createShared(vertx, JsonObject()
                .put("url", "jdbc:hsqldb:file:db/wiki")
                .put("driver_class", "org.hsqldb.jdbcDriver")
                .put("max_pool_size", 30))
        LOGGER.info("prepareDatabase sarting ${dbClient}")

        dbClient!!.getConnection { ar ->
            LOGGER.info("getting connection")
            when {
                ar.failed() -> {
                    LOGGER.error("Could not open a database connection", ar.cause())
                    promise1.fail(ar.cause())
                }
                else -> {
                    val connection = ar.result()
                    connection.execute(SQL_CREATE_PAGES_TABLE) { create ->
                        connection.close()
                        when {
                            create.failed() -> {
                                LOGGER.error("Database preparation error", create.cause())
                                promise1.fail(create.cause())
                            }
                            else -> {
                                LOGGER.info("connection established")
                                promise1.complete()
                            }
                        }

                    }
                }
            }
        }
        LOGGER.info("prepareDatabase ready ${promise1.future()}")
        return promise1.future()
    }

    private fun startHttpServer(): Future<Void> {
        LOGGER.info("startHttpServer ready")
        val promise1: Promise<Void> = Promise.promise()
        val server = vertx.createHttpServer()

        val router = Router.router(vertx)
        router.get("/").handler(this::indexHandler)
        router.get("/wiki/:page").handler(this::pageRenderingHandler)
        router.post().handler(BodyHandler.create()) // eats multi part form requests. json ?
        router.post("/save").handler(this::pageUpdateHandler)
        router.post("/create").handler(this::pageCreateHandler)
        router.post("/delete").handler(this::pageDeletionHandler)


        this.templateEngine = FreeMarkerTemplateEngine.create(vertx)
        server
                .requestHandler(router)
                .listen(8080) { ar ->
                    if (ar.succeeded()) {
                        LOGGER.info("HTTP server running on port 8080")
                        promise1.complete()
                    } else {
                        LOGGER.error("Could not start a HTTP server", ar.cause())
                        promise1.fail(ar.cause())
                    }
                }
        return promise1.future()
    }

    private fun indexHandler(context: RoutingContext) {
        LOGGER.info("walla... ")
        dbClient!!.getConnection { car ->
            if (car.succeeded()) {
                val connection = car.result()
                connection.query(SQL_ALL_PAGES) { res ->
                    connection.close()
                    LOGGER.info("all pages $res")
                    if (res.succeeded()) {
                        val pages = res.result()
                                .results
                                .map { it.getString(0) }
                                .sorted()
                        LOGGER.info("pages $pages")
                        context.put("title", "Wiki home")
                        context.put("pages", pages)
                        templateEngine!!.render(context.data(), "templates/index.ftl") { ar ->
                            LOGGER.info("rendered ftl $ar ")
                            if (ar.succeeded()) {
                                context.response().putHeader("Content-Type", "text/html")
                                context.response().end(ar.result())
                            } else {
                                ar.cause().printStackTrace()
                                LOGGER.info("rendered ftl failed ${ar.cause()} ")
                                context.fail(ar.cause())
                            }
                        }
                    } else context.fail(res.cause())
                }
            } else context.fail(car.cause())
        }
    }

    private fun pageRenderingHandler(context: RoutingContext) {
        val page = context.request().getParam("page")

        dbClient!!.getConnection { car ->
            if (car.succeeded()) {

                val connection = car.result()
                connection.queryWithParams(SQL_GET_PAGE, JsonArray().add(page)) { fetch ->
                    connection.close()
                    if (fetch.succeeded()) {
                        val row = fetch.result().results
                                .stream()
                                .findFirst()
                                .orElseGet {
                                    JsonArray().add(-1).add("""# A new page
                                        |
                                        |Feel-free to write in Markdown!""".trimMargin())
                                }
                        val id = row.getInteger(0)
                        val rawContent = row.getString(1)

                        context.put("title", page)
                        context.put("id", id)
                        context.put("newPage", if (fetch.result().results.isEmpty()) "yes" else "no")
                        context.put("rawContent", rawContent)
                        context.put("content", Processor.process(rawContent))
                        context.put("timestamp", Date().toString())

                        templateEngine!!.render(context.data(), "templates/page.ftl") { ar ->
                            if (ar.succeeded()) {
                                context.response().putHeader("Content-Type", "text/html")
                                context.response().end(ar.result())
                            } else {
                                context.fail(ar.cause())
                            }
                        }
                    } else {
                        context.fail(fetch.cause())
                    }
                }

            } else {
                context.fail(car.cause())
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


    private fun pageUpdateHandler(context: RoutingContext) {
        val id = context.request().getParam("id")
        val title = context.request().getParam("title")
        val markdown = context.request().getParam("markdown")
        val newPage = "yes" == context.request().getParam("newPage")

        dbClient!!.getConnection { car ->
            if (car.succeeded()) {
                val connection = car.result()
                val sql = if (newPage) SQL_CREATE_PAGE else SQL_SAVE_PAGE
                val params = JsonArray()
                if (newPage) {
                    params.add(title).add(markdown)
                } else {
                    params.add(markdown).add(id)
                }
                connection.updateWithParams(sql, params) { res ->
                    connection.close()
                    if (res.succeeded()) {
                        context.response().statusCode = 303
                        context.response().putHeader("Location", "/wiki/$title")
                        context.response().end()
                    } else {
                        context.fail(res.cause())
                    }
                }
            } else {
                context.fail(car.cause())
            }
        }
    }


    private fun pageDeletionHandler(context: RoutingContext) {
        val id = context.request().getParam("id")
        dbClient!!.getConnection { car ->
            if (car.succeeded()) {
                val connection = car.result()
                connection.updateWithParams(SQL_DELETE_PAGE, JsonArray().add(id)) { res ->
                    connection.close()
                    if (res.succeeded()) {
                        context.response().statusCode = 303
                        context.response().putHeader("Location", "/")
                        context.response().end()
                    } else {
                        context.fail(res.cause())
                    }
                }
            } else {
                context.fail(car.cause())
            }
        }
    }

}