package com.example.services

import io.ktor.server.application.*
import kotlinx.coroutines.*
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Сервис для работы с ClickHouse — аналитика и логирование.
 * 
 * ClickHouse используется для:
 * - Логирование всех API запросов
 * - Аналитика активности пользователей
 * - Статистика сообщений и чатов
 * - Мониторинг производительности
 */
class ClickHouseService(private val app: Application) {
    
    private val clickhouseUrl: String = System.getenv("CLICKHOUSE_URL") 
        ?: "jdbc:clickhouse://localhost:8123/default"
    private val clickhouseUser: String = System.getenv("CLICKHOUSE_USER") ?: "default"
    private val clickhousePassword: String = System.getenv("CLICKHOUSE_PASSWORD") ?: ""
    
    private val enabled: Boolean = System.getenv("CLICKHOUSE_ENABLED")?.toBoolean() ?: false
    
    // Буфер для batch-вставки логов (оптимизация производительности)
    private val logBuffer = ConcurrentLinkedQueue<LogEntry>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        if (enabled) {
            // Явная загрузка драйвера ClickHouse JDBC
            try {
                Class.forName("com.clickhouse.jdbc.ClickHouseDriver")
                app.log.info("📊 ClickHouse JDBC driver loaded successfully")
            } catch (e: ClassNotFoundException) {
                app.log.error("❌ ClickHouse JDBC driver not found: ${e.message}")
            }
            
            app.log.info("📊 ClickHouse analytics enabled: $clickhouseUrl")
            initializeTables()
            startBatchInsertJob()
        } else {
            app.log.info("📊 ClickHouse analytics disabled (set CLICKHOUSE_ENABLED=true to enable)")
        }
    }
    
    private fun getConnection(): Connection? {
        return try {
            DriverManager.getConnection(clickhouseUrl, clickhouseUser, clickhousePassword)
        } catch (e: Exception) {
            app.log.error("❌ ClickHouse connection failed: ${e.message}")
            null
        }
    }
    
    /**
     * Создание таблиц в ClickHouse
     */
    private fun initializeTables() {
        getConnection()?.use { conn ->
            conn.createStatement().use { stmt ->
                // Таблица API запросов
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS api_requests (
                        timestamp DateTime64(3),
                        request_id String,
                        user_id Nullable(String),
                        method String,
                        path String,
                        status_code UInt16,
                        duration_ms UInt32,
                        ip_address String,
                        user_agent String,
                        error_message Nullable(String)
                    ) ENGINE = MergeTree()
                    ORDER BY (timestamp, path)
                    TTL toDateTime(timestamp) + INTERVAL 90 DAY
                """.trimIndent())
                
                // Таблица активности пользователей
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS user_activity (
                        timestamp DateTime64(3),
                        user_id String,
                        action String,
                        entity_type String,
                        entity_id Nullable(String),
                        metadata String
                    ) ENGINE = MergeTree()
                    ORDER BY (timestamp, user_id, action)
                    TTL toDateTime(timestamp) + INTERVAL 180 DAY
                """.trimIndent())
                
                // Таблица статистики сообщений
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS message_stats (
                        date Date,
                        hour UInt8,
                        conversation_id String,
                        message_count UInt32,
                        unique_senders UInt32,
                        avg_message_length Float32
                    ) ENGINE = SummingMergeTree()
                    ORDER BY (date, hour, conversation_id)
                    TTL date + INTERVAL 365 DAY
                """.trimIndent())
                
                // Таблица метрик производительности
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS performance_metrics (
                        timestamp DateTime64(3),
                        metric_name String,
                        metric_value Float64,
                        tags String
                    ) ENGINE = MergeTree()
                    ORDER BY (timestamp, metric_name)
                    TTL toDateTime(timestamp) + INTERVAL 30 DAY
                """.trimIndent())
                
                app.log.info("✅ ClickHouse tables initialized")
            }
        }
    }
    
    /**
     * Фоновая задача для batch-вставки логов
     */
    private fun startBatchInsertJob() {
        scope.launch {
            while (isActive) {
                delay(5000) // Каждые 5 секунд
                flushLogs()
            }
        }
    }
    
    private fun flushLogs() {
        if (logBuffer.isEmpty()) return
        
        val batch = mutableListOf<LogEntry>()
        while (logBuffer.isNotEmpty() && batch.size < 1000) {
            logBuffer.poll()?.let { batch.add(it) }
        }
        
        if (batch.isEmpty()) return
        
        getConnection()?.use { conn ->
            val sql = """
                INSERT INTO api_requests 
                (timestamp, request_id, user_id, method, path, status_code, duration_ms, ip_address, user_agent, error_message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            
            conn.prepareStatement(sql).use { ps ->
                batch.forEach { log ->
                    ps.setObject(1, java.sql.Timestamp.from(log.timestamp))
                    ps.setString(2, log.requestId)
                    ps.setString(3, log.userId)
                    ps.setString(4, log.method)
                    ps.setString(5, log.path)
                    ps.setInt(6, log.statusCode)
                    ps.setLong(7, log.durationMs)
                    ps.setString(8, log.ipAddress)
                    ps.setString(9, log.userAgent)
                    ps.setString(10, log.errorMessage)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            app.log.debug("📊 Flushed ${batch.size} logs to ClickHouse")
        }
    }
    
    // ============ PUBLIC API ============
    
    /**
     * Логирование API запроса
     */
    fun logApiRequest(
        requestId: String = UUID.randomUUID().toString(),
        userId: String? = null,
        method: String,
        path: String,
        statusCode: Int,
        durationMs: Long,
        ipAddress: String = "",
        userAgent: String = "",
        errorMessage: String? = null
    ) {
        if (!enabled) return
        
        logBuffer.offer(LogEntry(
            timestamp = Instant.now(),
            requestId = requestId,
            userId = userId,
            method = method,
            path = path,
            statusCode = statusCode,
            durationMs = durationMs,
            ipAddress = ipAddress,
            userAgent = userAgent,
            errorMessage = errorMessage
        ))
    }
    
    /**
     * Логирование активности пользователя
     */
    fun logUserActivity(
        userId: String,
        action: String,
        entityType: String,
        entityId: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ) {
        if (!enabled) return
        
        scope.launch {
            getConnection()?.use { conn ->
                val sql = """
                    INSERT INTO user_activity (timestamp, user_id, action, entity_type, entity_id, metadata)
                    VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
                
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, java.sql.Timestamp.from(Instant.now()))
                    ps.setString(2, userId)
                    ps.setString(3, action)
                    ps.setString(4, entityType)
                    ps.setString(5, entityId)
                    ps.setString(6, metadata.entries.joinToString(",") { "${it.key}=${it.value}" })
                    ps.executeUpdate()
                }
            }
        }
    }
    
    /**
     * Запись метрики производительности
     */
    fun recordMetric(name: String, value: Double, tags: Map<String, String> = emptyMap()) {
        if (!enabled) return
        
        scope.launch {
            getConnection()?.use { conn ->
                val sql = """
                    INSERT INTO performance_metrics (timestamp, metric_name, metric_value, tags)
                    VALUES (?, ?, ?, ?)
                """.trimIndent()
                
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, java.sql.Timestamp.from(Instant.now()))
                    ps.setString(2, name)
                    ps.setDouble(3, value)
                    ps.setString(4, tags.entries.joinToString(",") { "${it.key}=${it.value}" })
                    ps.executeUpdate()
                }
            }
        }
    }
    
    // ============ ANALYTICS QUERIES ============
    
    /**
     * Получить статистику запросов за период
     */
    fun getRequestStats(hours: Int = 24): RequestStats? {
        if (!enabled) return null
        
        return getConnection()?.use { conn ->
            val sql = """
                SELECT 
                    count() as total_requests,
                    countIf(status_code >= 200 AND status_code < 300) as success_count,
                    countIf(status_code >= 400) as error_count,
                    avg(duration_ms) as avg_duration,
                    max(duration_ms) as max_duration,
                    uniqExact(user_id) as unique_users
                FROM api_requests
                WHERE timestamp >= now() - INTERVAL ? HOUR
            """.trimIndent()
            
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, hours)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        RequestStats(
                            totalRequests = rs.getLong("total_requests"),
                            successCount = rs.getLong("success_count"),
                            errorCount = rs.getLong("error_count"),
                            avgDurationMs = rs.getDouble("avg_duration"),
                            maxDurationMs = rs.getLong("max_duration"),
                            uniqueUsers = rs.getLong("unique_users")
                        )
                    } else null
                }
            }
        }
    }
    
    /**
     * Получить топ эндпоинтов по количеству запросов
     */
    fun getTopEndpoints(limit: Int = 10, hours: Int = 24): List<EndpointStats> {
        if (!enabled) return emptyList()
        
        return getConnection()?.use { conn ->
            val sql = """
                SELECT 
                    path,
                    method,
                    count() as request_count,
                    avg(duration_ms) as avg_duration,
                    countIf(status_code >= 400) as error_count
                FROM api_requests
                WHERE timestamp >= now() - INTERVAL ? HOUR
                GROUP BY path, method
                ORDER BY request_count DESC
                LIMIT ?
            """.trimIndent()
            
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, hours)
                ps.setInt(2, limit)
                ps.executeQuery().use { rs ->
                    val results = mutableListOf<EndpointStats>()
                    while (rs.next()) {
                        results.add(EndpointStats(
                            path = rs.getString("path"),
                            method = rs.getString("method"),
                            requestCount = rs.getLong("request_count"),
                            avgDurationMs = rs.getDouble("avg_duration"),
                            errorCount = rs.getLong("error_count")
                        ))
                    }
                    results
                }
            }
        } ?: emptyList()
    }
    
    /**
     * Получить активность пользователя
     */
    fun getUserActivityStats(userId: String, days: Int = 7): UserActivityStats? {
        if (!enabled) return null
        
        return getConnection()?.use { conn ->
            val sql = """
                SELECT 
                    count() as total_actions,
                    uniqExact(action) as unique_actions,
                    min(timestamp) as first_activity,
                    max(timestamp) as last_activity
                FROM user_activity
                WHERE user_id = ? AND timestamp >= now() - INTERVAL ? DAY
            """.trimIndent()
            
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, userId)
                ps.setInt(2, days)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        UserActivityStats(
                            userId = userId,
                            totalActions = rs.getLong("total_actions"),
                            uniqueActions = rs.getLong("unique_actions"),
                            firstActivity = rs.getTimestamp("first_activity")?.toInstant(),
                            lastActivity = rs.getTimestamp("last_activity")?.toInstant()
                        )
                    } else null
                }
            }
        }
    }
    
    fun shutdown() {
        flushLogs()
        scope.cancel()
    }
}

// ============ DATA CLASSES ============

data class LogEntry(
    val timestamp: Instant,
    val requestId: String,
    val userId: String?,
    val method: String,
    val path: String,
    val statusCode: Int,
    val durationMs: Long,
    val ipAddress: String,
    val userAgent: String,
    val errorMessage: String?
)

data class RequestStats(
    val totalRequests: Long,
    val successCount: Long,
    val errorCount: Long,
    val avgDurationMs: Double,
    val maxDurationMs: Long,
    val uniqueUsers: Long
)

data class EndpointStats(
    val path: String,
    val method: String,
    val requestCount: Long,
    val avgDurationMs: Double,
    val errorCount: Long
)

data class UserActivityStats(
    val userId: String,
    val totalActions: Long,
    val uniqueActions: Long,
    val firstActivity: Instant?,
    val lastActivity: Instant?
)
