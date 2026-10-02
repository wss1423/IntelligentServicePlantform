package com.sky.test;

import com.sky.config.RedisConfiguration;
import com.sky.constant.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存管理器单元测试
 * <p>
 * 只验证缓存管理器的配置逻辑，不连接真实 Redis，所以可以脱离 Redis 环境直接跑
 *
 * @Author: shun wang
 */
class RandomTtlCacheManagerTest {

    /**
     * 构造一个不干任何事的 RedisConnectionFactory
     * 缓存管理器的构造过程只持有它、并不建立连接，所以这里返回 null 就够了
     */
    private RedisConnectionFactory fakeConnectionFactory() {
        return (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class[]{RedisConnectionFactory.class},
                (proxy, method, args) -> null);
    }

    @Test
    void 每个缓存的过期时间都应该落在基础时间与基础时间加抖动之间() {
        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(fakeConnectionFactory());
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(RedisConstants.SETMEAL_CACHE_BASE_TTL));

        RedisConfiguration.RandomTtlRedisCacheManager cacheManager =
                new RedisConfiguration.RandomTtlRedisCacheManager(cacheWriter, defaultConfig);
        //模拟 Spring 容器对 InitializingBean 的回调
        cacheManager.afterPropertiesSet();

        //1、能按 cacheName 动态创建缓存（@Cacheable 就是靠这个能力拿到 cache 的）
        RedisCache setmealCache = (RedisCache) cacheManager.getCache(RedisConstants.SETMEAL_CACHE_NAME);
        assertNotNull(setmealCache, "应该能为 setmealName 动态创建缓存");

        //2、过期时间落在 [基础时间, 基础时间 + 随机抖动] 区间内
        long minTtl = RedisConstants.SETMEAL_CACHE_BASE_TTL;
        long maxTtl = RedisConstants.SETMEAL_CACHE_BASE_TTL + RedisConstants.SETMEAL_CACHE_RANDOM_TTL;
        long ttl = setmealCache.getCacheConfiguration().getTtl().getSeconds();
        assertTrue(ttl >= minTtl && ttl <= maxTtl,
                "过期时间应该在 " + minTtl + "~" + maxTtl + " 秒之间，实际为：" + ttl);

        //3、不同缓存的过期时间应该被打散，不能全都一样（否则起不到防止缓存雪崩的作用）
        Set<Long> ttlSet = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            RedisCache cache = (RedisCache) cacheManager.getCache("testCache" + i);
            assertNotNull(cache, "应该能为 testCache" + i + " 动态创建缓存");
            long cacheTtl = cache.getCacheConfiguration().getTtl().getSeconds();
            assertTrue(cacheTtl >= minTtl && cacheTtl <= maxTtl,
                    "过期时间应该在 " + minTtl + "~" + maxTtl + " 秒之间，实际为：" + cacheTtl);
            ttlSet.add(cacheTtl);
        }
        assertTrue(ttlSet.size() > 1, "不同缓存的过期时间应该被打散，实际只出现了这些值：" + ttlSet);
    }

}
