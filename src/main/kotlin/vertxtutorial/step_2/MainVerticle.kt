package vertxtutorial.step_2

import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Promise


class MainVerticle : AbstractVerticle() {


    @Throws(Exception::class)
    override fun start(promise: Promise<Void>) {

        val dbVerticleDeployment = Promise.promise<String>()
        vertx.deployVerticle(
                DbVerticle(),
                dbVerticleDeployment
        )
        dbVerticleDeployment.future().compose {
            val httpVerticleDeployment = Promise.promise<String>()
            vertx.deployVerticle(
                    httpVerticle::class.java,
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