package com.sky.component;

import com.sky.constant.RedisConstants;
import com.sky.mapper.DishMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

/**
 * 菜品布隆过滤器
 * <p>
 * 作用：兜底防止缓存穿透。
 * 缓存穿透指的是有人故意用「一定不存在的 id」反复请求接口，
 * 这类请求既不会命中缓存、数据库里也查不到，等于每次都白跑一趟数据库。
 * 布隆过滤器可以在查缓存之前就用极小的内存代价判断出「这个 id 一定不存在」，直接拦掉。
 * <p>
 * 说明：
 * 1、布隆过滤器只能新增、不能删除元素，所以菜品被删除后过滤器里仍然会有这个 id，
 * 这时它会返回「可能存在」，请求继续往下走到数据库，查不到再返回不存在即可 —— 这是布隆过滤器正常的容错行为；
 * 2、布隆过滤器存在误判率（这里配的是 1%），但只会「把不存在的判成存在」，绝不会「把存在的判成不存在」，
 * 所以不会出现真实菜品被误拦的情况。
 *
 * @Author: shun wang
 */
@Component
@Slf4j
public class DishBloomFilter {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private DishMapper dishMapper;

    /**
     * Redisson 提供的分布式布隆过滤器，底层是 Redis 的 bitmap
     */
    private RBloomFilter<Long> bloomFilter;

    /**
     * 布隆过滤器是否可用
     * Redis 挂掉时自动降级：不再拦截请求，让请求照常走缓存+数据库，避免影响主流程
     */
    private volatile boolean available = false;

    /**
     * 项目启动时：初始化布隆过滤器，并把数据库里已有的菜品 id 全部加载进去
     */
    @PostConstruct
    public void init() {
        try {
            bloomFilter = redissonClient.getBloomFilter(RedisConstants.DISH_BLOOM_FILTER_NAME);
            //tryInit 是幂等的：过滤器已经存在且参数一致时直接返回 false，不会清空已有数据
            //参数一：预估要存放的元素个数  参数二：允许的误判率
            bloomFilter.tryInit(RedisConstants.DISH_BLOOM_FILTER_EXPECTED_INSERTIONS,
                    RedisConstants.DISH_BLOOM_FILTER_FALSE_PROBABILITY);
            rebuild();
            available = true;
            log.info("菜品布隆过滤器初始化成功");
        } catch (Exception e) {
            //降级：布隆过滤器只是一个防穿透的兜底手段，挂了不应该影响正常的点餐业务
            available = false;
            log.error("菜品布隆过滤器初始化失败，已自动降级（不再拦截请求）：{}", e.getMessage());
        }
    }

    /**
     * 重建布隆过滤器：把数据库里所有菜品 id 重新加载进去
     * 使用场景：项目启动时初始化；后续数据量变化较大时也可以手动触发
     */
    public void rebuild() {
        List<Long> dishIds = dishMapper.getAllIds();
        if (dishIds == null || dishIds.isEmpty()) {
            log.warn("数据库中没有查询到菜品数据，布隆过滤器未加载任何元素");
            return;
        }
        for (Long dishId : dishIds) {
            bloomFilter.add(dishId);
        }
        //设置过期时间，避免长期不重建导致误判率升高
        bloomFilter.expire(Duration.ofSeconds(RedisConstants.DISH_BLOOM_FILTER_TTL));
        log.info("菜品布隆过滤器重建完成，本次加载菜品 {} 条，过滤器内当前元素数：{}", dishIds.size(), bloomFilter.count());
    }

    /**
     * 新增菜品时，把菜品 id 同步写进布隆过滤器
     * 注意：布隆过滤器不能删除元素，所以菜品被删除时不需要（也无法）从这里移除
     */
    public void add(Long dishId) {
        if (!available || dishId == null) {
            return;
        }
        try {
            bloomFilter.add(dishId);
        } catch (Exception e) {
            log.error("菜品[{}]写入布隆过滤器失败：{}", dishId, e.getMessage());
        }
    }

    /**
     * 判断菜品 id 是否「可能存在」
     *
     * @return false —— 一定不存在，可以直接拦截，不用再查缓存和数据库（这就是防缓存穿透）
     *         true  —— 可能存在（有 1% 的误判率），继续走后面的缓存 + 数据库查询
     */
    public boolean mightContain(Long dishId) {
        if (!available || dishId == null) {
            //过滤器不可用时全部放行，保证业务不受影响
            return true;
        }
        try {
            return bloomFilter.contains(dishId);
        } catch (Exception e) {
            log.error("查询菜品布隆过滤器失败，本次放行：{}", e.getMessage());
            return true;
        }
    }

}
