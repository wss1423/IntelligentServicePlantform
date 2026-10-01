package com.sky.constant;

/**
 * @Author: shun wang
 * @Date: 2025/11/30/22:29
 * @Description:
 */
public class RedisConstants {

    //店铺营业状态
    public static final String SHOP_STATUS = "SHOP_STATUS";

    /* ==================== 菜品缓存（手动缓存，防缓存穿透 / 缓存雪崩） ==================== */

    /**
     * 菜品缓存 key 前缀，完整 key 规则：dish_{分类id}
     * 说明：原来这个前缀是以字符串 "dish_" 硬编码在 Controller 中的，这里统一抽取成常量
     */
    public static final String DISH_CACHE_KEY_PREFIX = "dish_";

    /**
     * 菜品详情缓存 key 前缀，完整 key 规则：dish_detail_{菜品id}
     * 注意它同样以 dish_ 开头，所以管理端修改菜品时统一清理 dish_* 就能一起清掉
     */
    public static final String DISH_DETAIL_CACHE_KEY_PREFIX = "dish_detail_";

    /**
     * 菜品缓存的基础过期时间（秒）
     */
    public static final Long DISH_CACHE_BASE_TTL = 1800L;

    /**
     * 菜品缓存过期时间的随机抖动上限（秒）
     * 真实过期时间 = DISH_CACHE_BASE_TTL + [0, DISH_CACHE_RANDOM_TTL] 之间的随机数
     * 目的：避免大批量缓存同一时刻集中失效，从而防止缓存雪崩
     */
    public static final Long DISH_CACHE_RANDOM_TTL = 300L;

    /**
     * 空值缓存的过期时间（秒）
     * 数据库查不到数据时，也往 Redis 写一个空集合，并设置较短的过期时间
     * 目的：挡住"查一个必然不存在的数据"的反复请求，防止缓存穿透
     */
    public static final Long DISH_CACHE_NULL_TTL = 300L;

    /* ==================== 套餐缓存（Spring Cache 注解缓存） ==================== */

    /**
     * 套餐缓存的 cacheNames，与 @Cacheable(cacheNames = "setmealName") 遥相呼应
     */
    public static final String SETMEAL_CACHE_NAME = "setmealName";

    /**
     * 套餐缓存的基础过期时间（秒）
     */
    public static final Long SETMEAL_CACHE_BASE_TTL = 1800L;

    /**
     * 套餐缓存过期时间的随机抖动上限（秒），防止缓存雪崩
     */
    public static final Long SETMEAL_CACHE_RANDOM_TTL = 600L;

    /* ==================== 接口限流（Redis 滑动窗口） ==================== */

    /**
     * 限流 key 前缀，完整 key 规则：rate:limit:{接口}:{ip}
     */
    public static final String RATE_LIMIT_KEY_PREFIX = "rate:limit:";

    /* ==================== 布隆过滤器 ==================== */

    /**
     * 菜品布隆过滤器在 Redis 中的名称
     */
    public static final String DISH_BLOOM_FILTER_NAME = "bloom:filter:dish";

    /**
     * 布隆过滤器预估要存放的元素个数
     */
    public static final Long DISH_BLOOM_FILTER_EXPECTED_INSERTIONS = 10000L;

    /**
     * 布隆过滤器允许的误判率
     */
    public static final Double DISH_BLOOM_FILTER_FALSE_PROBABILITY = 0.01;

    /**
     * 布隆过滤器在 Redis 中的过期时间（秒），默认 7 天，避免长期不重建导致误判率升高
     */
    public static final Long DISH_BLOOM_FILTER_TTL = 7 * 24 * 60 * 60L;

}
