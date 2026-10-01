package com.sky.config;

import com.sky.constant.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

@Configuration
@Slf4j
public class RedisConfiguration {

    /**
     * redis 模板对象
     * <p>
     * 【本次修复】这个方法原来漏写了 @Bean 注解，导致它根本没有被注册成 Spring Bean，
     * 项目里各处 @Autowired 注入进来的其实是 SpringBoot 自动配置的 RedisTemplate
     * （key 用的是 JDK 序列化，在 redis 里看到的是一串二进制乱码）。
     * 现在补上 @Bean，key 就会按这里配置的字符串序列化器存储。
     * 方法体一行都没有改，只是加了注解。
     */
    @Bean
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory){
        log.info("开始创建redis模板对象");
        RedisTemplate redisTemplate = new RedisTemplate();
        //设置redis的链接工厂对象
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        //设置reids key 的序列化器
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        return redisTemplate;
    }

    /**
     * Spring Cache 的缓存管理器
     * <p>
     * @EnableCaching 打开之后，@Cacheable / @CacheEvict 这些注解才会生效，
     * 但注解缓存具体「存在哪个 cache、存多久」是由 CacheManager 决定的。
     * 项目原来没有配 CacheManager，SpringBoot 用的是默认配置，缓存是永不过期的，
     * 套餐缓存一旦写入就一直占着内存，而且所有缓存同时不过期也就谈不上雪崩保护。
     * <p>
     * 这里做的事情：
     * 1、给注解缓存设置过期时间；
     * 2、每个 cacheName 的过期时间 = 基础时间 + 随机抖动，防止大量缓存在同一时刻集中失效（缓存雪崩）。
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory){
        log.info("开始创建缓存管理器对象");

        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                //key 用字符串序列化，在 redis 里看到的是 setmealName::1 这种可读的形式
                //（这本来也是 SpringBoot 的默认值，写出来是为了明确）
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                //value 保持 SpringBoot 默认的 JDK 序列化（RedisSerializer.java()）：
                //因为被缓存的实体里含 LocalDateTime 字段，而 RedisSerializer.json()
                //用的 ObjectMapper 没有注册 JavaTimeModule，序列化时会对 LocalDateTime 直接报错。
                //.serializeValuesWith(RedisSerializationContext.SerializationPair
                //        .fromSerializer(RedisSerializer.json()))
                //基础过期时间，真实过期时间在下面的 createRedisCache 里再加随机抖动
                .entryTtl(Duration.ofSeconds(RedisConstants.SETMEAL_CACHE_BASE_TTL));

        return new RandomTtlRedisCacheManager(cacheWriter, defaultConfig);
    }

    /**
     * 自定义缓存管理器：给每个 cacheName 生成「基础过期时间 + 随机抖动」的独立过期时间
     * <p>
     * 举个例子：套餐缓存基础 1800 秒、抖动上限 600 秒，
     * 那么 setmealName 这个 cache 每次创建时过期时间会在 1800~2400 秒之间随机取一个值。
     * 如果以后又加了别的 cache（比如 categoryName），它拿到的是另一个随机值，
     * 这样各个缓存的失效时间就被打散了，不会出现"一大片缓存同时失效、请求全打到数据库"的情况。
     */
    public static class RandomTtlRedisCacheManager extends RedisCacheManager {

        public RandomTtlRedisCacheManager(RedisCacheWriter cacheWriter,
                                         RedisCacheConfiguration defaultCacheConfiguration) {
            // 最后一个参数 true：允许运行时按 cacheName 动态创建 cache
            super(cacheWriter, defaultCacheConfiguration, Collections.emptyMap(), true);
        }

        @Override
        protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfiguration) {
            long jitter = ThreadLocalRandom.current()
                    .nextLong(RedisConstants.SETMEAL_CACHE_RANDOM_TTL + 1);
            long ttl = RedisConstants.SETMEAL_CACHE_BASE_TTL + jitter;

            log.info("为缓存[{}]设置过期时间：{}秒（基础{}秒 + 随机抖动{}秒）",
                    name, ttl, RedisConstants.SETMEAL_CACHE_BASE_TTL, jitter);

            return super.createRedisCache(name, cacheConfiguration.entryTtl(Duration.ofSeconds(ttl)));
        }
    }

}
