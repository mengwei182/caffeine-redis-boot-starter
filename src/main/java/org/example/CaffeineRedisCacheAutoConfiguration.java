package org.example;

import org.example.listener.DefaultCacheEventListener;
import org.example.listener.Topic;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @author lihui
 * @since 2024/1/4
 */
@AutoConfiguration
public class CaffeineRedisCacheAutoConfiguration {
    @Bean
    public RedisTemplate<String, Object> caffeineRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        RedisSerializer<?> stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 默认的Caffeine-Redis缓存组件
     *
     * @return
     */
    @Bean
    public CaffeineRedisCache caffeineRedisCache(RedisCacheConfiguration redisCacheConfiguration, RedisConnectionFactory redisConnectionFactory, RedisTemplate<String, Object> caffeineRedisTemplate) {
        CaffeineRedisCacheManager caffeineRedisCacheManager = new CaffeineRedisCacheManager(redisCacheConfiguration, redisConnectionFactory, caffeineRedisTemplate);
        return (CaffeineRedisCache) caffeineRedisCacheManager.getCache(CaffeineRedisCache.class.getName());
    }

    @Bean
    public CacheProperties cacheProperties() {
        return new CacheProperties();
    }

    /**
     * RedisCacheConfiguration
     *
     * @param cacheProperties spring cache配置，以spring.cache开头
     * @return
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(CacheProperties cacheProperties) {
        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig().serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));
        if (cacheProperties != null) {
            CacheProperties.Redis redisProperties = cacheProperties.getRedis();
            if (redisProperties.getTimeToLive() != null) {
                redisCacheConfiguration = redisCacheConfiguration.entryTtl(redisProperties.getTimeToLive());
            }
            if (redisProperties.getKeyPrefix() != null) {
                redisCacheConfiguration = redisCacheConfiguration.prefixCacheNameWith(redisProperties.getKeyPrefix());
            }
            if (!redisProperties.isCacheNullValues()) {
                redisCacheConfiguration = redisCacheConfiguration.disableCachingNullValues();
            }
            if (!redisProperties.isUseKeyPrefix()) {
                redisCacheConfiguration = redisCacheConfiguration.disableKeyPrefix();
            }
        }
        return redisCacheConfiguration;
    }

    @Bean
    @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory redisConnectionFactory) {
        RedisMessageListenerContainer redisMessageListenerContainer = new RedisMessageListenerContainer();
        redisMessageListenerContainer.setConnectionFactory(redisConnectionFactory);
        return redisMessageListenerContainer;
    }

    @Bean
    public DefaultCacheEventListener redisKeyExpirationEventMessageListener(RedisMessageListenerContainer redisMessageListenerContainer, CaffeineRedisCache caffeineRedisCache) {
        DefaultCacheEventListener defaultCacheEventListener = new DefaultCacheEventListener(redisMessageListenerContainer, caffeineRedisCache);
        // key过期监听通道
        redisMessageListenerContainer.addMessageListener(defaultCacheEventListener, new PatternTopic(Topic.KEY_EXPIRATION_CHANNEL));
        // 自定义事件监听通道
        redisMessageListenerContainer.addMessageListener(defaultCacheEventListener, new ChannelTopic(Topic.CACHE_CHANNEL));
        return defaultCacheEventListener;
    }
}