package org.example.listener;

import lombok.extern.slf4j.Slf4j;
import org.example.CaffeineRedisCache;
import org.example.event.CacheEvent;
import org.example.event.CacheEventEnum;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 基于Redis事件发布订阅机制实现的分布式缓存数据同步监听器。
 * <p><b>不建议使用该监听器</b>，因为redis的消息订阅发布不会对消息做一致性验证，所以有可能出现订阅方接收不到消息导致分布式CaffeineRedisCache数据不一致。
 * <p>建议使用MQ中间件作为CaffeineRedisCache事件监听器，保证分布式CaffeineRedisCache的数据一致性。
 *
 * @author lihui
 * @since 2024/1/16
 */
@Slf4j
public class DefaultCacheEventListener implements MessageListener {
    private final CaffeineCache caffeineCache;
    private final RedisCache redisCache;
    /**
     * 参考bean：caffeineRedisTemplate
     */
    private final RedisTemplate<String, Object> redisTemplate;

    public DefaultCacheEventListener(CaffeineCache caffeineCache, RedisCache redisCache, RedisTemplate<String, Object> redisTemplate) {
        this.caffeineCache = caffeineCache;
        this.redisCache = redisCache;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = redisTemplate.getStringSerializer().deserialize(message.getChannel());
        if (channel == null || !channel.equals(ListenerChannel.CACHE_CHANNEL)) {
            return;
        }
        CacheEvent cacheEvent = (CacheEvent) redisTemplate.getValueSerializer().deserialize(message.getBody());
        if (cacheEvent == null) {
            return;
        }
        Object key = cacheEvent.getKey();
        String type = cacheEvent.getType();
        if (CacheEventEnum.EVICT_CAFFEINE.name().equals(type)) {
            log.debug("cache key evict:{}", key);
            try {
                synchronized (CaffeineRedisCache.LOCKS.computeIfAbsent(key, o -> new Object())) {
                    caffeineCache.evict(key);
                }
            } finally {
                CaffeineRedisCache.LOCKS.remove(key);
            }
        }
        if (CacheEventEnum.EVICT_ALL.name().equals(type)) {
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
        // 清空缓存类型
        if (CacheEventEnum.CLEAR.name().equals(type)) {
            log.debug("cache clear event");
            caffeineCache.clear();
            redisCache.clear();
        }
    }
}