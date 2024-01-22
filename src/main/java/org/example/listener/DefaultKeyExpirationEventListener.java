package org.example.listener;

import lombok.extern.slf4j.Slf4j;
import org.example.CaffeineRedisCache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.cache.RedisCache;

/**
 * 默认key过期监听器
 *
 * @author lihui
 * @since 2024/1/15
 */
@Slf4j
public class DefaultKeyExpirationEventListener implements KeyExpirationEventListener {
    private final CaffeineCache caffeineCache;
    private final RedisCache redisCache;

    public DefaultKeyExpirationEventListener(CaffeineCache caffeineCache, RedisCache redisCache) {
        this.caffeineCache = caffeineCache;
        this.redisCache = redisCache;
    }

    @Override
    public void onMessage(Object key) {
        if (key == null) {
            return;
        }
        log.debug("cache key expire:{}", key);
        try {
            synchronized (CaffeineRedisCache.LOCKS.computeIfAbsent(key, o -> new Object())) {
                // 同步删除caffeine缓存
                caffeineCache.evict(key);
                // 同步删除redis缓存
                redisCache.evict(key);
            }
        } finally {
            CaffeineRedisCache.LOCKS.remove(key);
        }
    }
}