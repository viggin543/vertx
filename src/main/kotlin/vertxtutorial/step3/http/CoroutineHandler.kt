package vertxtutorial.step3.http

import io.vertx.core.Verticle
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


interface CoroutineHandler: CoroutineScope {



    fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Unit) {
        handler { ctx ->
            launch(ctx.vertx().dispatcher()) {
                try {
                    fn(ctx)
                } catch (e: Exception) {
                    handleError(ctx,e)
                }
            }
        }
    }

    private fun handleError(context: RoutingContext, t:Throwable) {
        context.response().statusCode = 500
        context.response().putHeader("Content-Type", "application/json")
        context.response().end(JsonObject()
                .put("success", false)
                .put("error", t.message).encode())
    }
}






