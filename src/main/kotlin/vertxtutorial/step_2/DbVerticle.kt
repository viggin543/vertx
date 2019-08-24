package vertxtutorial.step_2

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.jdbc.JDBCClient
import vertxtutorial.config.DatabaseConstants.Companion.CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.stream.Collectors


class DbVerticle : AbstractVerticle() {


    private val LOGGER = LoggerFactory.getLogger(this::class.java.simpleName)

    private enum class SqlQuery {
        CREATE_PAGES_TABLE,
        ALL_PAGES,
        GET_PAGE,
        CREATE_PAGE,
        SAVE_PAGE,
        DELETE_PAGE
    }


    private var dbClient: JDBCClient? = null

    @Throws(Exception::class)
    override fun start(promise: Promise<Void>) {
        /*
   * Note: this uses blocking APIs, but data is small...
   */
        loadSqlQueries()
        dbClient = JDBCClient.createShared(vertx, JsonObject()
                .put("url", "jdbc:hsqldb:file:db/wiki")
                .put("driver_class", "org.hsqldb.jdbcDriver")
                .put("max_pool_size", 30))


        dbClient!!.query(sqlQueries[SqlQuery.CREATE_PAGES_TABLE]) { res ->
            when {
                res.succeeded() -> {
                    vertx.eventBus().consumer("wikidb.queue",
                            this::onMessage
                    )
                    promise.complete()
                }
                else -> {
                    LOGGER.error("Database preparation error", res.cause())
                    promise.fail(res.cause())
                }
            }
        }
    }

    private val sqlQueries = hashMapOf<SqlQuery, String>()

    @Throws(IOException::class)
    private fun loadSqlQueries() {

        val queriesFile = config().getString(CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE)
        val queriesInputStream: InputStream
        when {
            queriesFile != null -> queriesInputStream = FileInputStream(queriesFile)
            else -> queriesInputStream = javaClass.getResourceAsStream("/db-queries.properties")
        }

        val queriesProps = Properties()
        queriesProps.load(queriesInputStream)
        queriesInputStream.close()

        sqlQueries[SqlQuery.CREATE_PAGES_TABLE] = queriesProps.getProperty("create-pages-table")
        sqlQueries[SqlQuery.ALL_PAGES] = queriesProps.getProperty("all-pages")
        sqlQueries[SqlQuery.GET_PAGE] = queriesProps.getProperty("get-page")
        sqlQueries[SqlQuery.CREATE_PAGE] = queriesProps.getProperty("create-page")
        sqlQueries[SqlQuery.SAVE_PAGE] = queriesProps.getProperty("save-page")
        sqlQueries[SqlQuery.DELETE_PAGE] = queriesProps.getProperty("delete-page")
    }

    enum class ErrorCodes {
        NO_ACTION_SPECIFIED,
        BAD_ACTION,
        DB_ERROR
    }

    private fun onMessage(message: Message<JsonObject>) {

        if (!message.headers().contains("action")) {
            LOGGER.error("No action header specified for message with headers {} and body {}",
                    message.headers(), message.body().encodePrettily())
            message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal, "No action header specified")
            return
        }

        when (val action = message.headers().get("action")) {
            "all-pages" -> fetchAllPages(message)
            "get-page" -> fetchPage(message)
            "create-page" -> createPage(message)
            "save-page" -> savePage(message)
            "delete-page" -> deletePage(message)
            else -> message.fail(ErrorCodes.BAD_ACTION.ordinal, "Bad action: $action")
        }
    }

    private fun fetchAllPages(message: Message<JsonObject>) {
        dbClient!!.query(sqlQueries[SqlQuery.ALL_PAGES]) { res ->
            when {
                res.succeeded() -> {
                    val pages = res.result()
                            .results
                            .stream()
                            .map { json -> json.getString(0) }
                            .sorted()
                            .collect(Collectors.toList())
                    message.reply(JsonObject().put("pages", JsonArray(pages)))
                }
                else -> reportQueryError(message, res.cause())
            }
        }
    }

    private fun fetchPage(message: Message<JsonObject>) {
        val requestedPage = message.body().getString("page")
        val params = JsonArray().add(requestedPage)

        dbClient!!.queryWithParams(sqlQueries[SqlQuery.GET_PAGE], params) { fetch ->
            when {
                fetch.succeeded() -> {
                    val response = JsonObject()
                    val resultSet = fetch.result()
                    when {
                        resultSet.rows.isEmpty() -> response.put("found", false)
                        else -> {
                            response.put("found", true)
                            val row = resultSet.results[0]
                            response.put("id", row.getInteger(0))
                            response.put("rawContent", row.getString(1))
                        }
                    }
                    message.reply(response)
                }
                else -> reportQueryError(message, fetch.cause())
            }
        }
    }

    private fun createPage(message: Message<JsonObject>) {
        val request = message.body()
        val data = JsonArray()
                .add(request.getString("title"))
                .add(request.getString("markdown"))

        dbClient!!.updateWithParams(sqlQueries[SqlQuery.CREATE_PAGE], data) { res ->
            when {
                res.succeeded() -> message.reply("ok")
                else -> reportQueryError(message, res.cause())
            }
        }
    }

    private fun savePage(message: Message<JsonObject>) {
        val request = message.body()
        val data = JsonArray()
                .add(request.getString("markdown"))
                .add(request.getString("id"))

        dbClient!!.updateWithParams(sqlQueries[SqlQuery.SAVE_PAGE], data) { res ->
            when {
                res.succeeded() -> message.reply("ok")
                else -> reportQueryError(message, res.cause())
            }
        }
    }

    private fun deletePage(message: Message<JsonObject>) {
        val data = JsonArray().add(message.body().getString("id"))

        dbClient!!.updateWithParams(sqlQueries[SqlQuery.DELETE_PAGE], data) { res ->
            when {
                res.succeeded() -> message.reply("ok")
                else -> reportQueryError(message, res.cause())
            }
        }
    }

    private fun reportQueryError(message: Message<JsonObject>, cause: Throwable) {
        LOGGER.error("Database query error", cause)
        message.fail(ErrorCodes.DB_ERROR.ordinal, cause.message)
    }

}