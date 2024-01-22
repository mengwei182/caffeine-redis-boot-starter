package org.example.publisher;

import org.example.CaffeineRedisCache;

/**
 * CaffeineRedisCache事件发布者接口，实现此接口以实现分布式架构的数据一致性，默认实现为：{@link DefaultCacheEventPublisher}
 * <p>如需替换为自定义Publisher，使用方法：{@link CaffeineRedisCache#setCacheEventPublisher(CacheEventPublisher)}
 *
 * @author lihui
 * @since 2024/1/16
 */
public interface CacheEventPublisher {
    void publish(Object event);
}