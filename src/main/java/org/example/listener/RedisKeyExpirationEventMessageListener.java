package org.example.listener;

import lombok.extern.slf4j.Slf4j;
import org.example.CaffeineRedisCache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import javax.annotation.Resource;

/**
 * @author lihui
 * @since 2024/1/15
 */
@Slf4j
public class RedisKeyExpirationEventMessageListener extends KeyExpirationEventMessageListener {
    @Resource
    private CaffeineCache caffeineCache;
    @Resource
    private RedisCache redisCache;
    @Resource
    private RedisTemplate<?, ?> caffeineRedisTemplate;

    public RedisKeyExpirationEventMessageListener(RedisMessageListenerContainer listenerContainer) {
        super(listenerContainer);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = caffeineRedisTemplate.getStringSerializer().deserialize(message.getBody());
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