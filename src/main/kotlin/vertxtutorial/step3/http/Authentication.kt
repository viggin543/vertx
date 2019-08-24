package vertxtutorial.step3.http

import io.vertx.config.ConfigRetriever
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.jdbc.JDBCAuth
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine
import io.vertx.kotlin.config.getConfigAwait
import vertxtutorial.config.DatabaseConstants


private fun jdbcAuth(config: JsonObject, vertx: Vertx): JDBCAuth {
    return JDBCAuth.create(vertx, JDBCClient.createShared(vertx, JsonObject()
            .put("url", config.getString(DatabaseConstants.CONFIG_WIKIDB_JDBC_URL, DatabaseConstants.DEFAULT_WIKIDB_JDBC_URL))
            .put("driver_class", config.getString(DatabaseConstants.CONFIG_WIKIDB_JDBC_DRIVER_CLASS, DatabaseConstants.DEFAULT_WIKIDB_JDBC_DRIVER_CLASS))
            .put("max_pool_size", config.getInteger(DatabaseConstants.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, DatabaseConstants.DEFAULT_JDBC_MAX_POOL_SIZE))))
}


suspend fun Router.setupAuthentication( vertx: Vertx,
                               templateEngine: FreeMarkerTemplateEngine) {

    val config = ConfigRetriever.create(vertx).getConfigAwait()
    val auth = jdbcAuth(config,vertx)

    route().handler(CookieHandler.create())
    route().handler(BodyHandler.create())
    route().handler(SessionHandler.create(LocalSessionStore.create(vertx))
            .setAuthProvider(auth))

    val authHandler = RedirectAuthHandler.create(auth, "/login")
    route("/").handler(authHandler)
    route("/wiki/*").handler(authHandler)
    route("/action/*").handler(authHandler)

    get("/login").handler(loginHandler(templateEngine))
    post("/login-auth").handler(FormLoginHandler.create(auth))

    get("/logout").handler { context ->
        context.clearUser()
        context.response()
                .setStatusCode(302)
                .putHeader("Location", "/")
                .end()
    }


}

fun loginHandler(templateEngine : FreeMarkerTemplateEngine ) = { context: RoutingContext ->
        context.put("title", "Login")
        templateEngine.render(context.data(), "templates/login.ftl") { ar ->
            if (ar.succeeded()) {
                context.response().putHeader("Content-Type", "text/html")
                context.response().end(ar.result())
            } else {
                context.fail(ar.cause())
            }
        }
    }
