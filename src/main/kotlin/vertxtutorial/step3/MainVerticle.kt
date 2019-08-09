package vertxtutorial.step3

import io.vertx.core.DeploymentOptions;
import vertxtutorial.step3.database.WikiDatabaseVerticle
import vertxtutorial.step3.http.HttpServerVerticle
import io.vertx.kotlin.core.deployVerticleAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle

class MainVerticle : CoroutineVerticle() {

    override suspend fun start() {
        try {
            println(vertx.deployVerticleAwait(WikiDatabaseVerticle()))
            println(vertx.deployVerticleAwait(
                    "vertxtutorial.step3.http.HttpServerVerticle",
                    DeploymentOptions().setInstances(2)))
        } catch (err: Throwable) {
            println("could not start app")
            err.printStackTrace()
        }
    }
}