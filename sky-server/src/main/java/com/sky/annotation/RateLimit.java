package com.sky.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解，用于标识某个接口需要做「基于 Redis 滑动窗口的限流」
 * <p>
 * 使用方式：在 Controller 方法上标注 @RateLimit 即可，例如
 * <pre>
 *     &#64;RateLimit(window = 60, maxCount = 30)
 *     public Result&lt;List&lt;DishVO&gt;&gt; list(Long categoryId) { ... }
 * </pre>
 * 具体拦截逻辑见 com.sky.aspect.RateLimitAspect
 *
 * @Author: shun wang
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 滑动窗口的大小（秒），默认 60 秒
     */
    long window() default 60;

    /**
     * 一个滑动窗口内允许的最大请求次数，默认 30 次
     */
    long maxCount() default 30;

    /**
     * 限流维度的业务标识
     * 不写时默认取「类名.方法名」，最终生成的 key 规则为：rate:limit:{key}:{ip}
     */
    String key() default "";

}
