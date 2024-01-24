package org.example;

import lombok.Getter;
import lombok.Setter;
import org.example.event.CacheEvent;
import org.example.event.CacheEventEnum;
import org.example.listener.KeyExpirationEventListener;
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
import java.util.concurrent.TimeUnit;

/**
 * @author lihui
 * @since 2024/1/11
 */
public class CaffeineRedisCache extends AbstractValueAdaptingCache {
    public static final ConcurrentHashMap<Object, Object> LOCKS = new ConcurrentHashMap<>();
    private static final String DURATION_VALUE = "DURATION_VALUE";
    private final String name;
    @Getter
    private final CaffeineCache caffeineCache;
    @Getter
    private final RedisCache redisCache;
    @Getter
    private final RedisTemplate<String, Object> redisTemplate;
    @Getter
    @Setter
    private CacheEventPublisher cacheEventPublisher;
    @Getter
    @Setter
    private KeyExpirationEventListener keyExpirationEventListener;

    public CaffeineRedisCache(String name, CaffeineCache caffeineCache, RedisCache redisCache, RedisTemplate<String, Object> redisTemplate, CacheEventPublisher cacheEventPublisher, KeyExpirationEventListener keyExpirationEventListener) {
        super(true);
        this.name = name;
        this.caffeineCache = caffeineCache;
        this.redisCache = redisCache;
        this.redisTemplate = redisTemplate;
        this.cacheEventPublisher = cacheEventPublisher;
        this.keyExpirationEventListener = keyExpirationEventListener;
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

    /**
     * 设置一个带有过期时间的缓存
     *
     * @param key
     * @param value
     * @param duration 过期时间，最后会转为毫秒单位
     */
    public void put(Object key, Object value, Duration duration) {
        try {
            synchronized (LOCKS.computeIfAbsent(key, o -> new Object())) {
                caffeineCache.put(key, value);
                redisCache.put(key, value);
                if (duration != null) {
                    redisTemplate.opsForValue().set(key.toString(), DURATION_VALUE, duration.toMillis(), TimeUnit.MILLISECONDS);
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

    /**
     * 获取指定key的过期时间
     *
     * @param key
     * @return 单位为毫秒
     */
    public long getExpire(Object key) {
        Long expire = redisTemplate.getExpire(key.toString());
        return expire == null ? 0 : expire;
    }

    /**
     * 依托redis向指定通道发送事件
     *
     * @param channel 通道
     * @param message 信息
     */
    public void convertAndSend(String channel, Object message) {
        redisTemplate.convertAndSend(channel, message);
    }
}