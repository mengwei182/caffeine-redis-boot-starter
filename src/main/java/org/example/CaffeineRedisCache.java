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
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.nio.ByteBuffer;
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
    @Setter
    private CacheEventPublisher cacheEventPublisher;
    @Getter
    @Setter
    private KeyExpirationEventListener keyExpirationEventListener;

    public CaffeineRedisCache(String name, CaffeineCache caffeineCache, RedisCache redisCache, CacheEventPublisher cacheEventPublisher, KeyExpirationEventListener keyExpirationEventListener) {
        super(true);
        this.name = name;
        this.caffeineCache = caffeineCache;
        this.redisCache = redisCache;
        this.cacheEventPublisher = cacheEventPublisher;
        this.keyExpirationEventListener = keyExpirationEventListener;
    }

    @Override
    public <T> T get(@NonNull Object key, Class<T> type) {
        return lookup(key, type);
    }

    private <T> T lookup(@NonNull Object key, Class<T> type) {
        Assert.notNull(caffeineCache, "caffeine cache not found");
        T value = caffeineCache.get(key, type);
        if (value != null) {
            return value;
        }
        Assert.notNull(redisCache, "redis cache not found");
        value = redisCache.get(key, type);
        if (value != null) {
            // 设置到一级缓存里
            caffeineCache.put(key, value);
            // 发送事件通知，更新其他节点的caffeine cache
            cacheEventPublisher.publish(new CacheEvent(key, CacheEventEnum.UPDATE_KEY.name()));
            return value;
        }
        return null;
    }

    @Override
    protected Object lookup(@NonNull Object key) {
        Assert.notNull(caffeineCache, "caffeine cache not found");
        Object value = caffeineCache.get(key, Object.class);
        if (value != null) {
            return value;
        }
        Assert.notNull(redisCache, "redis cache not found");
        value = redisCache.get(key, Object.class);
        if (value != null) {
            // 设置到一级缓存里
            caffeineCache.put(key, value);
            // 发送事件通知，更新其他节点的caffeine cache
            cacheEventPublisher.publish(new CacheEvent(key, CacheEventEnum.UPDATE_KEY.name()));
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
        return lookup(key, valueLoader);
    }

    private <T> T lookup(@NonNull Object key, @NonNull Callable<T> valueLoader) {
        T call = caffeineCache.get(key, valueLoader);
        if (call != null) {
            return call;
        }
        call = redisCache.get(key, valueLoader);
        if (call != null) {
            // 设置到一级缓存里
            caffeineCache.put(key, call);
            // 发送事件通知，更新其他节点的caffeine cache
            cacheEventPublisher.publish(new CacheEvent(key, CacheEventEnum.UPDATE_KEY.name()));
        }
        return call;
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
     * @param duration 过期时间
     */
    public void put(Object key, Object value, @Nullable Duration duration) {
        try {
            synchronized (LOCKS.computeIfAbsent(key, o -> new Object())) {
                caffeineCache.put(key, value);
                ByteBuffer keyWrite = redisCache.getCacheConfiguration().getKeySerializationPair().write(key.toString());
                ByteBuffer valueWrite = redisCache.getCacheConfiguration().getValueSerializationPair().write(value);
                redisCache.getNativeCache().put(redisCache.getName(), keyWrite.array(), valueWrite.array(), duration);
                // 发送事件通知，更新其他节点的caffeine cache
                cacheEventPublisher.publish(new CacheEvent(key, CacheEventEnum.UPDATE_KEY.name()));
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
                cacheEventPublisher.publish(new CacheEvent(key, CacheEventEnum.EVICT_KEY.name()));
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