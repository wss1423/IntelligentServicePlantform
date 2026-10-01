package com.sky.aspect;

import com.sky.annotation.RateLimit;
import com.sky.constant.MessageConstant;
import com.sky.constant.RedisConstants;
import com.sky.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

/**
 * 接口限流切面：基于 Redis 的 ZSet 实现「滑动窗口限流」
 * <p>
 * 为什么用滑动窗口，而不是固定窗口计数？
 * 固定窗口（INCR + EXPIRE）在两个窗口交界处会被"打穿"，比如限制 60 秒 30 次，
 * 攻击者可以在第 59 秒打 30 次、第 61 秒再打 30 次，等于 2 秒内放进来 60 次。
 * 滑动窗口把每次请求的时间戳都记录在 ZSet 里，每次请求都实时统计
 * 「当前时间往前一个窗口内」的请求数，边界问题就不存在了。
 * <p>
 * ZSet 结构：score = 请求时间戳（毫秒），member = 时间戳 + 随机 UUID（保证唯一）
 * 一段时间的 key 形如：rate:limit:{接口}:{ip}
 *
 * @Author: shun wang
 */
@Aspect
@Component
@Slf4j
// 优先级调到最高：限流要拦在 @Cacheable 等其它切面之前，这样即使命中了缓存，请求也一样会被限流统计到
@Order(1)
public class RateLimitAspect {

    /**
     * 滑动窗口限流脚本
     * 整段逻辑放在 Redis 里用 Lua 原子执行，避免"先查后写"的并发问题：
     * 1、ZREMRANGEBYSCORE：把窗口之外的旧请求记录删掉（滑动窗口的"滑"就体现在这里）
     * 2、ZCARD：统计窗口内还剩下多少次请求
     * 3、没到阈值：把本次请求的时间戳写进 ZSet，顺便刷新 key 的过期时间，返回 1（放行）
     * 4、到阈值了：直接返回 0（拒绝）
     * 时间单位统一用毫秒
     */
    private static final String SLIDING_WINDOW_LUA =
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local limit = tonumber(ARGV[3])\n" +
            "local member = ARGV[4]\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window)\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "if count < limit then\n" +
            "    redis.call('ZADD', key, now, member)\n" +
            "    redis.call('PEXPIRE', key, window)\n" +
            "    return 1\n" +
            "end\n" +
            "return 0\n";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(SLIDING_WINDOW_LUA);
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    /**
     * 这里注入的是 StringRedisTemplate，而不是项目里的 RedisTemplate
     * 原因：RedisTemplate 的 value 用的是 JDK 序列化，Lua 脚本里 tonumber(ARGV[1]) 会解析失败；
     * StringRedisTemplate 的 key 和 value 都是字符串序列化，正好适合传时间戳、阈值这些参数
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 切入点：所有标注了 @RateLimit 的方法
     */
    @Pointcut("@annotation(com.sky.annotation.RateLimit)")
    public void rateLimitPointCut() {
    }

    /**
     * 环绕通知：先限流，通过了再执行原本的业务方法
     */
    @Around("rateLimitPointCut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = AnnotationUtils.findAnnotation(method, RateLimit.class);
        if (rateLimit == null) {
            return joinPoint.proceed();
        }

        //1、拼装限流 key：rate:limit:{接口}:{ip}
        String api = StringUtils.hasText(rateLimit.key())
                ? rateLimit.key()
                : method.getDeclaringClass().getSimpleName() + "." + method.getName();
        String ip = getClientIp();
        String key = RedisConstants.RATE_LIMIT_KEY_PREFIX + api + ":" + ip;

        //2、执行 Lua 脚本
        long now = System.currentTimeMillis();
        long windowMillis = rateLimit.window() * 1000;
        Long allowed = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(windowMillis),
                String.valueOf(rateLimit.maxCount()),
                now + "-" + UUID.randomUUID());

        //3、返回 0 说明窗口内请求数已经达到阈值，直接拒绝
        if (allowed == null || allowed == 0L) {
            log.warn("接口[{}]在{}秒内来自IP[{}]的请求次数已超过{}次，触发限流拦截",
                    api, rateLimit.window(), ip, rateLimit.maxCount());
            throw new RateLimitException(MessageConstant.RATE_LIMIT_EXCEEDED);
        }

        //4、放行
        return joinPoint.proceed();
    }

    /**
     * 获取客户端真实 IP
     * 项目线上是用 Nginx 反向代理的，直接拿 request.getRemoteAddr() 拿到的是 Nginx 的地址，
     * 所以优先从代理头 X-Forwarded-For / X-Real-IP 里取
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();

        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            //X-Forwarded-For 可能是「客户端IP, 代理1, 代理2」这种格式，取第一个
            int index = ip.indexOf(',');
            return index > 0 ? ip.substring(0, index).trim() : ip.trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        return request.getRemoteAddr();
    }

}
