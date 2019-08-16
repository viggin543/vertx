package vertxtutorial.step3.database


import io.vertx.core.Handler
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.serviceproxy.ServiceBinder
import java.io.IOException
import java.util.*

class WikiDatabaseVerticle(private val dbClient: JDBCClient) : CoroutineVerticle() {

    override suspend fun start() {

        val sqlQueries = loadSqlQueries()

        WikiDatabaseServiceFactory.create(dbClient, sqlQueries, Handler { serviceImpl ->
            if (serviceImpl.succeeded()) {
                ServiceBinder(vertx) // published WikiDatabaseService as WikiDatabaseServiceImpl. like guice bind.
                        .setAddress(CONFIG_WIKIDB_QUEUE)
                        .register(WikiDatabaseService::class.java, serviceImpl.result())
            } else throw RuntimeException("shit... could not get connectin... ")
        })
    }

    /*
   * Note: this uses blocking APIs, but data is small...
   */
    @Throws(IOException::class)
    private fun loadSqlQueries(): HashMap<SqlQuery, String> {
        val queriesProps = Properties()
        javaClass.getResourceAsStream("/db-queries.properties").use {
            queriesProps.load(it)
        }
        val sqlQueries = HashMap<SqlQuery, String>()
        sqlQueries[SqlQuery.CREATE_PAGES_TABLE] = queriesProps.getProperty("create-pages-table")
        sqlQueries[SqlQuery.ALL_PAGES] = queriesProps.getProperty("all-pages")
        sqlQueries[SqlQuery.GET_PAGE] = queriesProps.getProperty("get-page")
        sqlQueries[SqlQuery.CREATE_PAGE] = queriesProps.getProperty("create-page")
        sqlQueries[SqlQuery.SAVE_PAGE] = queriesProps.getProperty("save-page")
        sqlQueries[SqlQuery.DELETE_PAGE] = queriesProps.getProperty("delete-page")
        sqlQueries[SqlQuery.OPA_PAGE] = queriesProps.getProperty("delete-page")
        return sqlQueries
    }

    companion object {
        const val CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url"
        const val CONFIG_WIKIDB_JDBC_DRIVER_CLASS = "wikidb.jdbc.driver_class"
        const val CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size"
        const val CONFIG_WIKIDB_QUEUE = "wikidb.queue"
    }
}
