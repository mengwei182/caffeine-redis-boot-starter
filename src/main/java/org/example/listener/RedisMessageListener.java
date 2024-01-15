package org.example.listener;

import lombok.extern.slf4j.Slf4j;
import org.example.CaffeineRedisCache;
import org.example.event.CacheEven;
import org.example.event.CacheEventEnum;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;

import javax.annotation.Resource;

/**
 * @author lihui
 * @since 2023/4/25
 */
@Slf4j
public class RedisMessageListener implements MessageListener {
    @Resource
    private CaffeineCache caffeineCache;
    @Resource
    private RedisCache redisCache;
    @Resource
    private RedisTemplate<?, ?> caffeineRedisTemplate;

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        String channel = caffeineRedisTemplate.getStringSerializer().deserialize(message.getChannel());
        if (channel == null || !channel.equals(ListenerChannel.CACHE_CHANNEL)) {
            return;
        }
        CacheEven cacheEven = (CacheEven) caffeineRedisTemplate.getValueSerializer().deserialize(message.getBody());
        if (cacheEven == null) {
            return;
        }
        String type = cacheEven.getType();
        if (CacheEventEnum.EVICT.name().equals(type)) {
            Object key = cacheEven.getKey();
            log.debug("cache key evict:{}", key);
            try {
                synchronized (CaffeineRedisCache.LOCKS.computeIfAbsent(key, o -> new Object())) {
                    caffeineCache.evict(key);
                    redisCache.evict(key);
                }
            } finally {
                CaffeineRedisCache.LOCKS.remove(key);
            }
        }
        if (CacheEventEnum.CLEAR.name().equals(type)) {
            log.debug("cache clear event");
            caffeineCache.clear();
            redisCache.clear();
        }
    }
}