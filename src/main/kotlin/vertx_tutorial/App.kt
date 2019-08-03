package vertx_tutorial
import io.vertx.core.Vertx.vertx

import org.slf4j.LoggerFactory
import vertx_tutorial.step_2.MainVerticle

val logger = LoggerFactory.getLogger("main")
fun main(args: Array<String>) {
    val vertx = vertx()
    logger.info("so it begins...")
    logger.info(args.contentDeepToString())
    vertx.deployVerticle(MainVerticle())
}

