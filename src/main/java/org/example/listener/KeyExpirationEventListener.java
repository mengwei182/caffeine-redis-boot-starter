package org.example.listener;

import org.example.CaffeineRedisCache;
import org.springframework.data.redis.connection.Message;

/**
 * key过期事件监听器，上游为Redis的key过期事件：{@link DefaultCacheEventListener#onMessage(Message, byte[])}。默认实现为：{@link DefaultKeyExpirationEventListener}
 * 如需替换为自定义Listener，使用方法：{@link CaffeineRedisCache#setKeyExpirationEventListener(KeyExpirationEventListener)}
 *
 * @author lihui
 * @since 2024/1/22
 */
public interface KeyExpirationEventListener {
    void onMessage(Object key);
}