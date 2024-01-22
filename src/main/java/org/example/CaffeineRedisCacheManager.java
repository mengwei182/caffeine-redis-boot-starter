package org.example;

import org.example.listener.DefaultKeyExpirationEventListener;
import org.example.publisher.DefaultCacheEventPublisher;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author lihui
 * @since 2024/1/16
 */
public class CaffeineRedisCacheManager implements CacheManager {
    private final Map<String, Cache> cacheMap = new ConcurrentHashMap<>();
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisCacheConfiguration redisCacheConfiguration;
    private final RedisConnectionFactory redisConnectionFactory;

    public CaffeineRedisCacheManager(RedisCacheConfiguration redisCacheConfiguration, RedisConnectionFactory redisConnectionFactory, RedisTemplate<String, Object> redisTemplate) {
        this.redisCacheConfiguration = redisCacheConfiguration;
        this.redisConnectionFactory = redisConnectionFactory;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Cache getCache(@NonNull String name) {
        Cache cache = this.cacheMap.get(name);
        if (cache == null) {
            CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
            CaffeineCache caffeineCache = (CaffeineCache) caffeineCacheManager.getCache(name);
            RedisCacheManager redisCacheManager = RedisCacheManager.builder().cacheDefaults(redisCacheConfiguration).cacheWriter(RedisCacheWriter.lockingRedisCacheWriter(redisConnectionFactory)).build();
            RedisCache redisCache = (RedisCache) redisCacheManager.getCache(name);
            cache = this.cacheMap.computeIfAbsent(name, v -> new CaffeineRedisCache(name, caffeineCache, redisCache, redisTemplate, new DefaultCacheEventPublisher(redisTemplate), new DefaultKeyExpirationEventListener(caffeineCache, redisCache)));
        }
        return cache;
    }

    @NonNull
    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(this.cacheMap.keySet());
    }
}