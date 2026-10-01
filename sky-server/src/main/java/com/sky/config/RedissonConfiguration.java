package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 配置类
 * <p>
 * 项目里原本已经引入了 spring-boot-starter-data-redis（用来操作 RedisTemplate），
 * 这里额外再引入一个 Redisson 客户端，专门用来使用它提供的分布式布隆过滤器 RBloomFilter。
 * Redisson 客户端和原来的 RedisTemplate 各自独立，原来的缓存代码一行都没有改动。
 */
@Configuration
@Slf4j
public class RedissonConfiguration {

    /**
     * 复用 application.yml / application-dev.yml 里已经配置好的 redis 连接信息
     */
    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private int port;

    @Value("${spring.redis.password:}")
    private String password;

    @Value("${spring.redis.database:0}")
    private int database;

    /**
     * Redisson 客户端
     * destroyMethod = "shutdown"：Spring 容器关闭时释放 Redisson 的连接和线程资源
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        log.info("开始创建 Redisson 客户端对象, address=redis://{}:{}", host, port);

        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                //连接池与超时参数，按项目实际情况调整
                .setConnectionMinimumIdleSize(4)
                .setConnectionPoolSize(16)
                .setTimeout(3000)
                .setConnectTimeout(5000);

        //没有配密码就不要设置，否则会报认证错误
        if (StringUtils.hasText(password)) {
            config.useSingleServer().setPassword(password);
        }

        return Redisson.create(config);
    }

}
