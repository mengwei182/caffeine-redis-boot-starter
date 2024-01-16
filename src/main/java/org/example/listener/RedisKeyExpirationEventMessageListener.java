package org.example.listener;

import lombok.extern.slf4j.Slf4j;
import org.example.CaffeineRedisCache;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Duration;

/**
 * 基于Redis实现的key过期监听器，用以实现{@link CaffeineRedisCache#put(String, Object, Duration)}方法
 *
 * @author lihui
 * @since 2024/1/15
 */
@Slf4j
public class RedisKeyExpirationEventMessageListener extends KeyExpirationEventMessageListener {
    private final CaffeineRedisCache caffeineRedisCache;

    public RedisKeyExpirationEventMessageListener(RedisMessageListenerContainer listenerContainer, CaffeineRedisCache caffeineRedisCache) {
        super(listenerContainer);
        this.caffeineRedisCache = caffeineRedisCache;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = caffeineRedisCache.getRedisTemplate().getStringSerializer().deserialize(message.getBody());
        if (key == null) {
            return;
        }
        log.debug("cache key expire:{}", key);
        try {
            synchronized (CaffeineRedisCache.LOCKS.computeIfAbsent(key, o -> new Object())) {
                // 同步删除caffeine缓存
                caffeineRedisCache.getCaffeineCache().evict(key);
                // 同步删除redis缓存
                caffeineRedisCache.getRedisCache().evict(key);
            }
        } finally {
            CaffeineRedisCache.LOCKS.remove(key);
        }
    }
}