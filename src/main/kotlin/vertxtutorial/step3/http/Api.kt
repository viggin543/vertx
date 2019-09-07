package vertxtutorial.step3.http

import com.github.rjeschke.txtmark.Processor
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineVerticle
import mu.KLogging
import vertxtutorial.step3.database.*
import java.util.*


class Api( val dbService: WikiDatabaseService) : CoroutineVerticle() {

    companion object : KLogging()

     suspend fun apiCreatePage(context: RoutingContext) {
        val page = context.bodyAsJson
        if (!validateJsonPageDocument(context, page, "name", "markdown")) {
            return
        }
        dbService.createPageAwait(page.getString("name"), page.getString("markdown"))

        context.response().statusCode = 201
        context.response().putHeader("Content-Type", "application/json")
        context.response().end(JsonObject().put("success", true).encode())

    }

     suspend fun apiUpdatePage(context: RoutingContext) {
        val id = Integer.valueOf(context.request().getParam("id"))
        val page = context.bodyAsJson
        if (!validateJsonPageDocument(context, page, "markdown")) {
            return
        }
        dbService.savePageAwait(id, page.getString("markdown"))
        handleSimpleDbReply(context)
    }

     suspend  fun apiDeletePage(context: RoutingContext) {
        val id = Integer.valueOf(context.request().getParam("id"))
        dbService.deletePageAwait(id)
        handleSimpleDbReply(context)
    }

     fun handleSimpleDbReply(context: RoutingContext) {
        context.response().statusCode = 200
        context.response().putHeader("Content-Type", "application/json")
        context.response().end(JsonObject().put("success", true).encode())
    }


     fun validateJsonPageDocument(context: RoutingContext, page: JsonObject, vararg expectedKeys: String): Boolean {
        if (!Arrays.stream(expectedKeys).allMatch { page.containsKey(it) }) {
            logger.error("Bad page creation JSON payload: " + page.encodePrettily() + " from " + context.request().remoteAddress())
            context.response().statusCode = 400
            context.response().putHeader("Content-Type", "application/json")
            context.response().end(JsonObject()
                    .put("success", false)
                    .put("error", "Bad request payload").encode())
            return false
        }
        return true
    }

     suspend fun apiGetPage(context: RoutingContext) {
        val id = Integer.valueOf(context.request().getParam("id"))
        val response = JsonObject()
        val dbObject = dbService.fetchPageByIdAwait(id)
        if (dbObject.getBoolean("found")) {
            val payload = JsonObject()
                    .put("name", dbObject.getString("name"))
                    .put("id", dbObject.getInteger("id"))
                    .put("markdown", dbObject.getString("content"))
                    .put("html", Processor.process(dbObject.getString("content")))
            response
                    .put("success", true)
                    .put("page", payload)
            context.response().statusCode = 200
        } else {
            context.response().statusCode = 404
            response
                    .put("success", false)
                    .put("error", "There is no page with ID $id")
        }

    }

     suspend fun apiRoot(context: RoutingContext) {
        val pages = dbService.fetchAllPagesDataAwait()
         logger.info("attempting to log mdc context as json")
        val response = JsonObject()
                .put("success", true)
                .put("pages", pages.map {
                    JsonObject()
                            .put("id", it.getInteger("ID"))
                            .put("name", it.getString("NAME"))
                })
        with(context.response()) {
            statusCode = 200
            putHeader("Content-Type", "application/json")
            end(response.encode())
        }
    }


}