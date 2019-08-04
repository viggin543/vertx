package vertxtutorial.step3.database

import io.vertx.codegen.annotations.Fluent
import io.vertx.codegen.annotations.GenIgnore
import io.vertx.codegen.annotations.ProxyGen
import io.vertx.codegen.annotations.VertxGen
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.coroutines.awaitResult


enum class SqlQuery {
    CREATE_PAGES_TABLE,
    ALL_PAGES,
    GET_PAGE,
    CREATE_PAGE,
    SAVE_PAGE,
    DELETE_PAGE
}


object WikiDatabaseServiceFactory {
    @GenIgnore
    @JvmStatic
    fun create(dbClient: JDBCClient, sqlQueries: HashMap<SqlQuery, String>, readyHandler: Handler<AsyncResult<WikiDatabaseService>>):WikiDatabaseService {
        return WikiDatabaseServiceImpl(dbClient, sqlQueries, readyHandler)

    }
    @GenIgnore
    @JvmStatic
    fun createProxy(vertx: Vertx, address:String ):WikiDatabaseService {
        return WikiDatabaseServiceVertxEBProxy(vertx, address)
    }
}

@ProxyGen
@VertxGen
interface WikiDatabaseService {
    @Fluent
    fun fetchAllPages(resultHandler: Handler<AsyncResult<JsonArray>>):WikiDatabaseService

    @Fluent
    fun fetchPage(name:String,resultHandler: Handler<AsyncResult<JsonObject>>):WikiDatabaseService

    @Fluent
    fun createPage(title:String, markdown:String, resultHandler: Handler<AsyncResult<Void>>):WikiDatabaseService

    @Fluent
    fun savePage(id:Int, markdown:String,resultHandler: Handler<AsyncResult<Void>>):WikiDatabaseService

    @Fluent
    fun deletePage(id:Int, resultHandler: Handler<AsyncResult<Void>>):WikiDatabaseService

}


suspend fun WikiDatabaseService.fetchAllPagesAwait(): JsonArray {
    return awaitResult {
        this.fetchAllPages(it)
    }
}

suspend fun WikiDatabaseService.fetchPageAwait(name: String): JsonObject {
    return awaitResult {
        this.fetchPage(name, it)
    }
}

suspend fun WikiDatabaseService.createPageAwait(title: String, markdown: String): Unit {
    return awaitResult {
        this.createPage(title, markdown, Handler { ar -> it.handle(ar.mapEmpty()) })
    }
}

suspend fun WikiDatabaseService.savePageAwait(id: Int, markdown: String): Unit {
    return awaitResult {
        this.savePage(id, markdown, Handler { ar -> it.handle(ar.mapEmpty()) })
    }
}

suspend fun WikiDatabaseService.deletePageAwait(id: Int): Unit {
    return awaitResult {
        this.deletePage(id, Handler { ar -> it.handle(ar.mapEmpty()) })
    }
}

