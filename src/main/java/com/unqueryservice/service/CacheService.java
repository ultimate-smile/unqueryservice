package com.unqueryservice.service;

import com.unqueryservice.config.QueryServiceProperties;
import com.unqueryservice.model.QueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed cache for query results.
 *
 * <p>Cache key format: {@code query:<dataSource>:<sha256(sql+params)>}
 * This ensures that the same SQL on different data sources never collides.
 *
 * <p>In the "test" profile this bean is excluded; tests provide a {@code @MockBean}
 * so no real Redis connection is required.
 */
@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class CacheService {

    private static final String KEY_PREFIX = "query:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final QueryServiceProperties properties;

    /**
     * Retrieves a cached result.
     *
     * @return an {@link Optional} containing the cached result, or empty if not cached
     */
    public Optional<QueryResult> get(String cacheKey) {
        if (!isCacheEnabled()) {
            return Optional.empty();
        }
        try {
            Object value = redisTemplate.opsForValue().get(cacheKey);
            if (value instanceof QueryResult result) {
                log.debug("Cache HIT for key '{}'", cacheKey);
                return Optional.of(result);
            }
        } catch (Exception ex) {
            log.warn("Cache GET failed for key '{}': {}", cacheKey, ex.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Stores a query result in the cache.
     */
    public void put(String cacheKey, QueryResult result) {
        if (!isCacheEnabled()) {
            return;
        }
        try {
            Duration ttl = Duration.ofSeconds(properties.getCacheTtlSeconds());
            redisTemplate.opsForValue().set(cacheKey, result, ttl);
            log.debug("Cache SET for key '{}' (TTL={}s)", cacheKey, properties.getCacheTtlSeconds());
        } catch (Exception ex) {
            log.warn("Cache PUT failed for key '{}': {}", cacheKey, ex.getMessage());
        }
    }

    /**
     * Evicts all cached results for a given data source (prefix-based delete).
     */
    public void evictDataSource(String dataSourceName) {
        try {
            String pattern = KEY_PREFIX + dataSourceName + ":*";
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Evicted {} cache entries for data source '{}'", keys.size(), dataSourceName);
            }
        } catch (Exception ex) {
            log.warn("Cache eviction failed for data source '{}': {}", dataSourceName, ex.getMessage());
        }
    }

    /**
     * Builds a deterministic cache key from the data source name and the SQL query.
     */
    public String buildCacheKey(String dataSourceName, String sql) {
        String raw = dataSourceName + ":" + sql;
        return KEY_PREFIX + dataSourceName + ":" + sha256Hex(raw);
    }

    private boolean isCacheEnabled() {
        return properties.getCacheTtlSeconds() > 0;
    }

    private String sha256Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed to be available in every JVM
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
