package vertxtutorial.config

interface DatabaseConstants {
    companion object {
        const val CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url"
        const val CONFIG_WIKIDB_JDBC_DRIVER_CLASS = "wikidb.jdbc.driver_class"
        const val CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size"

        const val DEFAULT_WIKIDB_JDBC_URL = "jdbc:hsqldb:file:db/wiki"
        const val DEFAULT_WIKIDB_JDBC_DRIVER_CLASS = "org.hsqldb.jdbcDriver"
        const val DEFAULT_JDBC_MAX_POOL_SIZE = 30
        const val CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file"
        const val CONFIG_WIKIDB_QUEUE = "wikidb.queue"
    }
}