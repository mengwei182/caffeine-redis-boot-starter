package org.example;

import com.github.benmanes.caffeine.cache.Cache;
import org.example.event.CacheEven;
import org.example.event.CacheEventEnum;
import org.example.listener.ListenerChannel;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author lihui
 * @since 2024/1/11
 */
public class CaffeineRedisCache {
    public static final ConcurrentHashMap<Object, Object> LOCKS = new ConcurrentHashMap<>();
    @Resource
    private CaffeineCache caffeineCache;
    @Resource
    private RedisCache redisCache;
    @Resource
    private RedisTemplate<Object, Object> caffeineRedisTemplate;

    public Object get(Object key) {
        Assert.notNull(caffeineCache, "caffeine cache not found:" + key);
        Object value = caffeineCache.get(key, Object.class);
        if (value != null) {
            return value;
        }
        Assert.notNull(redisCache, "redis cache not found:" + key);
        value = redisCache.get(key, Object.class);
        if (value != null) {
            // 设置到一级缓存里
            caffeineCache.put(key, value);
            return value;
        }
        return null;
    }

    public <T> T get(Object key, Class<T> type) {
        T value = caffeineCache.get(key, type);
        if (value != null) {
            return value;
        }
        value = redisCache.get(key, type);
        if (value != null) {
            // 设置到一级缓存里
            caffeineCache.put(key, value);
            return value;
        }
        return null;
    }

    public Map<Object, Object> getAll() {
        Cache<Object, Object> caffeineNativeCache = caffeineCache.getNativeCache();
        if (caffeineNativeCache.asMap() != null) {
            return caffeineNativeCache.asMap();
        }
        return null;
    }

    public void put(String key, Object value) {
        try {
            synchronized (LOCKS.computeIfAbsent(key, o -> new Object())) {
                caffeineCache.put(key, value);
                redisCache.put(key, value);
            }
        } finally {
            LOCKS.remove(key);
        }
    }

    public void put(String key, Object value, Duration duration) {
        try {
            synchronized (LOCKS.computeIfAbsent(key, o -> new Object())) {
                caffeineCache.put(key, value);
                redisCache.put(key, value);
                String keySerialization = redisCache.getCacheConfiguration().getKeySerializationPair().read(ByteBuffer.wrap(key.getBytes()));
                caffeineRedisTemplate.opsForValue().set(keySerialization, value, duration);
            }
        } finally {
            LOCKS.remove(key);
        }
    }

    public void evict(Object key) {
        try {
            synchronized (LOCKS.computeIfAbsent(key, o -> new Object())) {
                caffeineCache.evict(key);
                redisCache.evict(key);
                // 发送redis事件通知，删除其他节点的key
                caffeineRedisTemplate.convertAndSend(ListenerChannel.CACHE_CHANNEL, new CacheEven(key, CacheEventEnum.EVICT.name()));
            }
        } finally {
            LOCKS.remove(key);
        }
    }

    public void clear() {
        caffeineCache.clear();
        redisCache.clear();
        // 发送redis事件通知，清空其他节点的key
        caffeineRedisTemplate.convertAndSend(ListenerChannel.CACHE_CHANNEL, new CacheEven(null, CacheEventEnum.CLEAR.name()));
    }
}