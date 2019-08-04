package vertxtutorial.step3

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import vertxtutorial.step3.database.WikiDatabaseVerticle
import vertxtutorial.step3.http.HttpServerVerticle


class MainVerticle : AbstractVerticle() {

    @Throws(Exception::class)
    override fun start(promise: Promise<Void>) {

        val dbVerticleDeployment = Promise.promise<String>()
        vertx.deployVerticle(WikiDatabaseVerticle(), dbVerticleDeployment)

        dbVerticleDeployment.future().compose {
            val httpVerticleDeployment = Promise.promise<String>()
            vertx.deployVerticle(
                    HttpServerVerticle::class.java,
                    DeploymentOptions().setInstances(2),
                    httpVerticleDeployment)

            httpVerticleDeployment.future()

        }.setHandler { ar ->
            if (ar.succeeded()) {
                promise.complete()
            } else {
                promise.fail(ar.cause())
            }
        }
    }
}