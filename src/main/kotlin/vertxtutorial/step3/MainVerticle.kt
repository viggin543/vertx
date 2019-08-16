package vertxtutorial.step3

import io.vertx.core.DeploymentOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import vertxtutorial.step3.database.WikiDatabaseVerticle
import vertxtutorial.step3.http.HttpServerVerticle
import io.vertx.kotlin.core.deployVerticleAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle

class MainVerticle : CoroutineVerticle() {

    override suspend fun start() {
        try {
            val client = JDBCClient.createShared(vertx,
                    JsonObject().put("url", config.getString(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:file:db/wiki"))
                            .put("driver_class", config.getString(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_DRIVER_CLASS, "org.hsqldb.jdbcDriver"))
                            .put("max_pool_size", config.getInteger(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 30)))
            vertx.deployVerticleAwait(WikiDatabaseVerticle(client))
            vertx.deployVerticleAwait(
                    HttpServerVerticle::class.qualifiedName.toString(),
                    DeploymentOptions().setInstances(2))
        } catch (err: Throwable) {
            println("could not start app")
            err.printStackTrace()
        }
    }
}