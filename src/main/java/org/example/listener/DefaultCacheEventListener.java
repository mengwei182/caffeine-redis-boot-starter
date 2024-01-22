package org.example.listener;

import lombok.extern.slf4j.Slf4j;
import org.example.CaffeineRedisCache;
import org.example.event.CacheEvent;
import org.example.event.CacheEventEnum;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 基于Redis事件发布订阅机制实现的分布式缓存数据同步监听器。
 * <p><b>不建议使用该监听器</b>，因为redis的消息订阅发布不会对消息做一致性验证，所以有可能出现订阅方接收不到消息导致分布式CaffeineRedisCache数据不一致。
 * <p>建议使用MQ中间件作为CaffeineRedisCache事件监听器，保证分布式CaffeineRedisCache的数据一致性。
 *
 * @author lihui
 * @since 2024/1/16
 */
@Slf4j
public class DefaultCacheEventListener extends KeyExpirationEventMessageListener implements MessageListener {
    private final CaffeineRedisCache caffeineRedisCache;

    public DefaultCacheEventListener(RedisMessageListenerContainer redisMessageListenerContainer, CaffeineRedisCache caffeineRedisCache) {
        super(redisMessageListenerContainer);
        this.caffeineRedisCache = caffeineRedisCache;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = caffeineRedisCache.getRedisTemplate().getStringSerializer().deserialize(message.getChannel());
        if (channel == null) {
            return;
        }
        // key过期事件
        if (channel.equals(ListenerChannel.KEY_EXPIRATION_CHANNEL)) {
            String key = caffeineRedisCache.getRedisTemplate().getStringSerializer().deserialize(message.getBody());
            if (key == null) {
                return;
            }
            log.debug("cache key expire:{}", key);
            caffeineRedisCache.getKeyExpirationEventListener().onMessage(key);
            return;
        }
        // 其他事件
        if (channel.equals(ListenerChannel.CACHE_CHANNEL)) {
            CacheEvent cacheEvent = (CacheEvent) caffeineRedisCache.getRedisTemplate().getValueSerializer().deserialize(message.getBody());
            if (cacheEvent == null) {
                return;
            }
            Object key = cacheEvent.getKey();
            String type = cacheEvent.getType();
            if (CacheEventEnum.EVICT_CAFFEINE.name().equals(type)) {
                log.debug("cache key evict:{}", key);
                try {
                    synchronized (CaffeineRedisCache.LOCKS.computeIfAbsent(key, o -> new Object())) {
                        caffeineRedisCache.getCaffeineCache().evict(key);
                    }
                } finally {
                    CaffeineRedisCache.LOCKS.remove(key);
                }
            }
            if (CacheEventEnum.EVICT_ALL.name().equals(type)) {
                log.debug("cache key evict:{}", key);
                try {
                    synchronized (CaffeineRedisCache.LOCKS.computeIfAbsent(key, o -> new Object())) {
                        caffeineRedisCache.getCaffeineCache().evict(key);
                        caffeineRedisCache.getRedisCache().evict(key);
                    }
                } finally {
                    CaffeineRedisCache.LOCKS.remove(key);
                }
            }
            // 清空缓存类型
            if (CacheEventEnum.CLEAR.name().equals(type)) {
                log.debug("cache clear event");
                caffeineRedisCache.getCaffeineCache().clear();
                caffeineRedisCache.getRedisCache().clear();
            }
        }
    }
}