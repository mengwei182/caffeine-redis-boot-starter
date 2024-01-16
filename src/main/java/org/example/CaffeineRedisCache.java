package org.example;

import lombok.Getter;
import lombok.Setter;
import org.example.event.CacheEvent;
import org.example.event.CacheEventEnum;
import org.example.publisher.CacheEventPublisher;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author lihui
 * @since 2024/1/11
 */
public class CaffeineRedisCache extends AbstractValueAdaptingCache {
    public static final ConcurrentHashMap<Object, Object> LOCKS = new ConcurrentHashMap<>();
    private final String name;
    @Getter
    private final CaffeineCache caffeineCache;
    @Getter
    private final RedisCache redisCache;
    @Getter
    private final RedisTemplate<String, Object> redisTemplate;
    @Setter
    private CacheEventPublisher cacheEventPublisher;

    public CaffeineRedisCache(String name, CaffeineCache caffeineCache, RedisCache redisCache, RedisTemplate<String, Object> redisTemplate, CacheEventPublisher cacheEventPublisher) {
        super(true);
        this.name = name;
        this.caffeineCache = caffeineCache;
        this.redisCache = redisCache;
        this.redisTemplate = redisTemplate;
        this.cacheEventPublisher = cacheEventPublisher;
    }

    @Override
    public <T> T get(@NonNull Object key, Class<T> type) {
        // 优先lookup查找，如果查到有效值则直接从一级缓存取值即可，如果没有查到则表明key没有关联值
        Object value = lookup(key);
        if (value == null) {
            return null;
        }
        return caffeineCache.get(key, type);
    }

    @Override
    protected Object lookup(@NonNull Object key) {
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

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public Object getNativeCache() {
        return caffeineCache;
    }

    @Override
    public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
        try {
            T call = valueLoader.call();
            if (call == null) {
                return null;
            }
            Object value = lookup(key);
            if (value == null) {
                put(key, call);
            }
            return call;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void put(@NonNull Object key, Object value) {
        put(key, value, null);
    }

    public void put(Object key, Object value, Duration duration) {
        try {
            synchronized (LOCKS.computeIfAbsent(key, o -> new Object())) {
                caffeineCache.put(key, value);
                redisCache.put(key, value);
                if (duration != null) {
                    redisTemplate.opsForValue().set(key.toString(), value, duration);
                }
                // 发送事件通知，删除其他节点的caffeine cache
                cacheEventPublisher.publish(new CacheEvent(key, duration, CacheEventEnum.EVICT_CAFFEINE.name()));
            }
        } finally {
            LOCKS.remove(key);
        }
    }

    @Override
    public void evict(@NonNull Object key) {
        try {
            synchronized (LOCKS.computeIfAbsent(key, o -> new Object())) {
                caffeineCache.evict(key);
                redisCache.evict(key);
                // 发送事件通知，删除其他节点的key
                cacheEventPublisher.publish(new CacheEvent(key, CacheEventEnum.EVICT_ALL.name()));
            }
        } finally {
            LOCKS.remove(key);
        }
    }

    @Override
    public void clear() {
        caffeineCache.clear();
        redisCache.clear();
        // 发送事件通知，清空其他节点的key
        cacheEventPublisher.publish(new CacheEvent(CacheEventEnum.CLEAR.name()));
    }
}