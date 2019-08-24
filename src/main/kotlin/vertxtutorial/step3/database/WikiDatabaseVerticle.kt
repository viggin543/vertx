package vertxtutorial.step3.database


import io.vertx.core.Handler
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.serviceproxy.ServiceBinder
import vertxtutorial.config.DatabaseConstants.Companion.CONFIG_WIKIDB_QUEUE
import java.io.IOException
import java.util.*

class WikiDatabaseVerticle(private val dbClient: JDBCClient) : CoroutineVerticle() {

    override suspend fun start() {

        WikiDatabaseServiceFactory.create(dbClient, loadSqlQueries(), Handler { serviceImpl ->
            if (serviceImpl.succeeded()) {
                ServiceBinder(vertx) // published WikiDatabaseService as WikiDatabaseServiceImpl. like guice bind.
                        .setAddress(CONFIG_WIKIDB_QUEUE)
                        .register(WikiDatabaseService::class.java, serviceImpl.result())
            } else throw RuntimeException("shit... could not get connectin... ")
        })
    }

    @Throws(IOException::class)
    private fun loadSqlQueries(): HashMap<SqlQuery, String> {
         val queriesProps = Properties().apply {
             javaClass.getResourceAsStream("/db-queries.properties").use {
                 load(it)
             }
        }
        fun prop(key: String) = queriesProps.getProperty(key)
        return hashMapOf(
                (SqlQuery.CREATE_PAGES_TABLE to prop("create-pages-table")),
                (SqlQuery.ALL_PAGES to prop( "all-pages")),
                (SqlQuery.GET_PAGE to prop("get-page")),
                (SqlQuery.CREATE_PAGE to prop("create-page")),
                (SqlQuery.SAVE_PAGE to prop("save-page")),
                (SqlQuery.DELETE_PAGE to prop("delete-page")),
                (SqlQuery.GET_PAGE_BY_ID to prop("get-page-by-id")),
                (SqlQuery.ALL_PAGES_DATA to prop("all-pages-data"))
        )
    }
}
