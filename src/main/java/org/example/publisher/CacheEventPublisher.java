package org.example.publisher;

import org.example.CaffeineRedisCache;
import org.example.event.CacheEvent;

/**
 * CaffeineRedisCache事件发布者顶级接口，实现此接口可以支持分布式架构下的数据一致性，默认实现为：{@link DefaultCacheEventPublisher}
 * <p>如需替换为自定义Publisher，使用API：{@link CaffeineRedisCache#setCacheEventPublisher(CacheEventPublisher)}
 *
 * @author lihui
 * @since 2024/1/16
 */
public interface CacheEventPublisher {
    void publish(CacheEvent event);
}