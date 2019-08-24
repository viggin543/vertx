package step3;

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.ext.sql.getConnectionAwait
import vertxtutorial.config.DatabaseConstants.Companion.CONFIG_WIKIDB_JDBC_DRIVER_CLASS
import vertxtutorial.config.DatabaseConstants.Companion.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE
import vertxtutorial.config.DatabaseConstants.Companion.CONFIG_WIKIDB_JDBC_URL



suspend fun dbClientForTest(conf: JsonObject, vertx: Vertx): JDBCClient {
    val client = JDBCClient.createShared(vertx,
            JsonObject().put("url", conf.getString(CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:file:db/wiki"))
                    .put("driver_class", conf.getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, "org.hsqldb.jdbcDriver"))
                    .put("max_pool_size", conf.getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 30)))
    client.getConnectionAwait()
    return client
}
