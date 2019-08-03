package vertx_tutorial

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine


class GuideToJavaDevs : AbstractVerticle() {
    val SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)"
    val SQL_GET_PAGE = "select Id, Content from Pages where Name = ?"
    val SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)"
    val SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?"
    val SQL_ALL_PAGES = "select Name from Pages"
    val SQL_DELETE_PAGE = "delete from Pages where Id = ?"

    private var dbClient: JDBCClient? = null
    private var templateEngine: FreeMarkerTemplateEngine? = null
    private val LOGGER = LoggerFactory.getLogger(GuideToJavaDevs::class.java.simpleName)

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

            if (!ar.failed()) {
                val connection = ar.result()
                connection.execute(SQL_CREATE_PAGES_TABLE) { create ->
                    connection.close()
                    if (create.failed()) {
                        LOGGER.error("Database preparation error", create.cause())
                        promise1.fail(create.cause())
                    } else {
                        LOGGER.info("connection established")
                        promise1.complete()
                    }
                }
            } else {
                LOGGER.error("Could not open a database connection", ar.cause())
                promise1.fail(ar.cause())
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
//        router.get("/wiki/:page").handler(this::pageRenderingHandler)
        router.post().handler(BodyHandler.create())
//        router.post("/save").handler(this::pageUpdateHandler)
//        router.post("/create").handler(this::pageCreateHandler)
//        router.post("/delete").handler(this::pageDeletionHandler)


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


}