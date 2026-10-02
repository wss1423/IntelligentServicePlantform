package com.sky.test;

import com.sky.annotation.RateLimit;
import com.sky.aspect.RateLimitAspect;
import com.sky.constant.MessageConstant;
import com.sky.constant.RedisConstants;
import com.sky.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 限流切面单元测试
 * <p>
 * 用 AspectJProxyFactory 走的是和 Spring 容器里一样的 AOP 代理流程，
 * 但 Redis 调用被替换成了一个假的 StringRedisTemplate（只返回放行/拒绝，不真的连 Redis），
 * 所以这个测试不需要 Redis 环境也能跑，主要验证三件事：
 * 1、没超过阈值时正常放行业务方法；
 * 2、超过阈值时抛出 RateLimitException 且业务方法不执行；
 * 3、传给 Lua 脚本的参数和限流 key 的格式是否正确。
 */
class RateLimitAspectTest {

    /**
     * 被限流的测试目标：60秒内最多3次
     */
    public static class TestController {

        int callCount = 0;

        @RateLimit(window = 60, maxCount = 3, key = "testApi")
        public String list(Long categoryId) {
            callCount++;
            return "ok:" + categoryId;
        }
    }

    private StubStringRedisTemplate stubTemplate;
    private TestController target;
    private TestController proxy;

    @BeforeEach
    void setUp() throws Exception {
        stubTemplate = new StubStringRedisTemplate();

        //切面依赖的 StringRedisTemplate 是私有字段，这里用反射把它换成假的实现
        RateLimitAspect aspect = new RateLimitAspect();
        Field field = RateLimitAspect.class.getDeclaredField("stringRedisTemplate");
        field.setAccessible(true);
        field.set(aspect, stubTemplate);

        target = new TestController();
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(aspect);
        proxy = proxyFactory.getProxy();
    }

    @Test
    void 未超过阈值时应该正常放行业务方法() {
        //Lua 脚本返回 1 表示放行
        stubTemplate.setNextResult(1L);

        assertEquals("ok:1", proxy.list(1L));
        assertEquals(1, target.callCount, "业务方法应该被执行了一次");
    }

    @Test
    void 超过阈值时应该抛出限流异常并且不执行业务方法() {
        //Lua 脚本返回 0 表示窗口内请求数已达阈值，拒绝
        stubTemplate.setNextResult(0L);

        RateLimitException exception = assertThrows(RateLimitException.class, () -> proxy.list(1L));
        assertEquals(MessageConstant.RATE_LIMIT_EXCEEDED, exception.getMessage());
        assertEquals(0, target.callCount, "被限流时业务方法不应该执行");
    }

    @Test
    void 限流key应该按照前缀加接口加ip的规则拼装() {
        stubTemplate.setNextResult(1L);

        proxy.list(1L);

        assertEquals(1, stubTemplate.capturedKeys.size(), "一次请求应该只执行一次限流脚本");
        String key = stubTemplate.capturedKeys.get(0);
        assertTrue(key.startsWith(RedisConstants.RATE_LIMIT_KEY_PREFIX + "testApi:"),
                "限流key应该以 " + RedisConstants.RATE_LIMIT_KEY_PREFIX + "testApi: 开头，实际为：" + key);
    }

    @Test
    void 传给lua脚本的四个参数应该是时间戳_窗口毫秒数_阈值_唯一member() {
        stubTemplate.setNextResult(1L);

        proxy.list(1L);

        assertEquals(1, stubTemplate.capturedArgs.size());
        Object[] args = stubTemplate.capturedArgs.get(0);
        assertEquals(4, args.length, "Lua 脚本需要 4 个参数");

        long now = Long.parseLong((String) args[0]);
        long window = Long.parseLong((String) args[1]);
        long limit = Long.parseLong((String) args[2]);
        String member = (String) args[3];

        //窗口单位是毫秒：注解里写的 60 秒，到脚本里应该是 60000
        assertEquals(60000L, window);
        assertEquals(3L, limit);
        assertTrue(Math.abs(System.currentTimeMillis() - now) < 5000, "第一个参数应该是当前毫秒时间戳");
        //member 必须以时间戳开头并带上唯一后缀，否则同一毫秒内的多个请求会因为 ZSet member 相同而互相覆盖，少算请求次数
        assertTrue(member.startsWith(now + "-"), "member 应该以时间戳开头，实际为：" + member);
    }

    /**
     * 假的 StringRedisTemplate：不连 Redis，只按预设结果返回"放行/拒绝"，并记录调用参数
     */
    private static class StubStringRedisTemplate extends StringRedisTemplate {

        private final AtomicLong nextResult = new AtomicLong(1L);
        private final List<String> capturedKeys = new ArrayList<>();
        private final List<Object[]> capturedArgs = new ArrayList<>();

        StubStringRedisTemplate() {
            super(fakeConnectionFactory());
        }

        void setNextResult(long result) {
            this.nextResult.set(result);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            capturedKeys.addAll(keys);
            capturedArgs.add(args);
            return (T) Long.valueOf(nextResult.get());
        }
    }

    /**
     * 构造一个不干任何事的 RedisConnectionFactory
     * StringRedisTemplate 的构造只持有它、并不建立连接
     */
    private static RedisConnectionFactory fakeConnectionFactory() {
        return (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class[]{RedisConnectionFactory.class},
                (p, method, args) -> null);
    }

}
