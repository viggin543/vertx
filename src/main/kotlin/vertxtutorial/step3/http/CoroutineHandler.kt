package vertxtutorial.step3.http

import io.vertx.core.Verticle
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.slf4j.MDCContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.MDC


interface CoroutineHandler: CoroutineScope {



    fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Unit): Route = handler { ctx ->
        GlobalScope.launch(ctx.vertx().dispatcher()) {
            val mdc = MDC.getCopyOfContextMap()
            mdc["headers"] = requestHeadersToJson(ctx).encode()
            withContext(MDCContext(mdc)) {
                try {
                    fn(ctx)
                } catch (e: Exception) {
                    handleError(ctx,e)
                }
            }
        }
    }

    private fun requestHeadersToJson(ctx: RoutingContext) = JsonObject().apply {
        ctx.request()?.headers()?.entries()?.forEach {
            this.put(it.key, it.value)
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






