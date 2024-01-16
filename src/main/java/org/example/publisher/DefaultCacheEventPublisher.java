package org.example.publisher;

import org.example.event.CacheEvent;
import org.example.listener.ListenerChannel;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 基于Redis事件发布订阅机制实现的分布式缓存数据同步Publisher
 *
 * @author lihui
 * @since 2024/1/16
 */
public class DefaultCacheEventPublisher implements CacheEventPublisher {
    private final RedisTemplate<String, Object> redisTemplate;

    public DefaultCacheEventPublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publish(CacheEvent event) {
        // 发送redis事件通知，更新其他节点的key
        redisTemplate.convertAndSend(ListenerChannel.CACHE_CHANNEL, event);
    }
}