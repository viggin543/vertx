package vertxtutorial.step3

import io.vertx.core.DeploymentOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.core.deployVerticleAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import vertxtutorial.config.DatabaseConstants.Companion.CONFIG_WIKIDB_JDBC_DRIVER_CLASS
import vertxtutorial.config.DatabaseConstants.Companion.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE
import vertxtutorial.config.DatabaseConstants.Companion.CONFIG_WIKIDB_JDBC_URL
import vertxtutorial.config.DatabaseConstants.Companion.DEFAULT_JDBC_MAX_POOL_SIZE
import vertxtutorial.config.DatabaseConstants.Companion.DEFAULT_WIKIDB_JDBC_DRIVER_CLASS
import vertxtutorial.config.DatabaseConstants.Companion.DEFAULT_WIKIDB_JDBC_URL
import vertxtutorial.step3.database.WikiDatabaseVerticle
import vertxtutorial.step3.http.HttpServerVerticle
import vertxtutorial.step3.http.AuthInitializerVerticle




class MainVerticle : CoroutineVerticle() {

    override suspend fun start() {
        try {
            val client = connect()

            vertx.deployVerticleAwait(WikiDatabaseVerticle(client))
            vertx.deployVerticleAwait(AuthInitializerVerticle())
            vertx.deployVerticleAwait(
                    // in order to use DI, just create the verticle with Guice.
                    // And inject it with a factory that will create children verticles if necessary...
                    HttpServerVerticle::class.qualifiedName.toString(),
                    DeploymentOptions().setInstances(2))
        } catch (err: Throwable) {
            println("could not start app")
            err.printStackTrace()
        }
    }

    private fun connect(): JDBCClient {
        return JDBCClient.createShared(vertx, JsonObject()
                .put("url", config.getString(CONFIG_WIKIDB_JDBC_URL, DEFAULT_WIKIDB_JDBC_URL))
                .put("driver_class", config.getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, DEFAULT_WIKIDB_JDBC_DRIVER_CLASS))
                .put("max_pool_size", config.getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, DEFAULT_JDBC_MAX_POOL_SIZE)))
    }
}