### 接入说明

集成了caffeine和redis作为缓存工具，caffeine作为一级缓存，redis作为二级缓存。
默认基于redis的发布订阅机制和过期key监听机制，支持设置动态的过期时间缓存，支持分布式缓存事件监听和key过期事件监听。

```
<!--caffeine redis cache-->
<dependency>
    <groupId>org.example</groupId>
    <artifactId>caffeine-redis-boot-starter</artifactId>
    <version>1.0</version>
</dependency>
```

### 接入流程

#### 引入jar包

直接添加caffeine-redis-boot-starter.jar包到项目或者安装到maven仓库后再引用

#### 配置参数

配置需要的spring.redis参数即可。

### 使用说明

```
@Resource
private CaffeineRedisCache caffeineRedisCache;
```

###

替换为自定义的缓存事件发布器：

```
CaffeineRedisCache.setCacheEventPublisher(CacheEventPublisher cacheEventPublisher)
```

###

替换为自定义的key过期事件监听器：

```
CaffeineRedisCache.setKeyExpirationEventListener(KeyExpirationEventListener keyExpirationEventListener)
```