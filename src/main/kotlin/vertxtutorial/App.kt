package vertxtutorial

import io.vertx.config.ConfigRetriever
import io.vertx.core.Vertx.vertx
import io.vertx.kotlin.config.getConfigAwait
import io.vertx.kotlin.core.deployVerticleAwait
import org.slf4j.LoggerFactory
import vertxtutorial.step3.MainVerticle


val logger = LoggerFactory.getLogger("main")




suspend fun main(args: Array<String>) {
    with(vertx()){
        val retriever = ConfigRetriever.create(this)
        val result= retriever.getConfigAwait()
        println("reading config json from default path")
        println(result.getString("FOO"))
        logger.info("so it begins...")
        logger.info(args.contentDeepToString())
        try {
            deployVerticleAwait(MainVerticle())
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

}

