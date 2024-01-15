### 接入说明
集成了caffeine和redis作为缓存工具，caffeine作为一级缓存，redis作为二级缓存。
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
```
spring:
  redis:
    host: xxx.xxx.xxx.xxx
    database: 1
    port: 6379
    password: xxx
    timeout: 3000ms
    lettuce:
      pool:
        enabled: true
        min-idle: 0
        max-idle: 8
        max-active: 8
        max-wait: 180000
```
配置基本的redis参数即可。
### 使用说明
```
@Resource
private CaffeineRedisCache caffeineRedisCache;
```