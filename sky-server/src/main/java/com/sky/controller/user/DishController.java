package com.sky.controller.user;

import com.sky.annotation.RateLimit;
import com.sky.component.DishBloomFilter;
import com.sky.constant.MessageConstant;
import com.sky.constant.RedisConstants;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 菜品布隆过滤器，兜底防止缓存穿透
     */
    @Autowired
    private DishBloomFilter dishBloomFilter;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    @RateLimit(window = 60, maxCount = 100)
    public Result<List<DishVO>> list(Long categoryId) {

        //构造redis中的key 规则：dish_分类id
        String key = RedisConstants.DISH_CACHE_KEY_PREFIX + categoryId;

        //查询redis中是否存在菜品数据
        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        if (list != null && list.size() > 0){
            //若存在 直接返回 无需查询数据库
            return Result.success(list);
        }

        // ==================== 【补全1：空值缓存，防止缓存穿透】 ====================
        // 原来的判断是 list != null && list.size() > 0，
        // 这样即使我们往redis里存了空集合，也会因为 size()==0 被当成"没有缓存"，
        // 于是每一次请求还是要查一遍数据库，空值缓存就白存了。
        // 正确做法：只要 key 存在（哪怕是空集合），就说明"数据库里确实没有这个分类的起售菜品"，
        // 直接返回空集合，不再回源数据库。
        // 下面这两行是原来的写法，保留作对比：
//        if (list != null && list.size() > 0){
//            //若存在 直接返回 无需查询数据库
//            return Result.success(list);
//        }
        if (list != null) {
            log.info("菜品缓存命中（空值缓存），分类id：{}", categoryId);
            return Result.success(list);
        }

        //若不存在 查询数据库 将查询到的数据放到redis中

        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品

        list = dishService.listWithFlavor(dish);

        // ==================== 【补全1：空值缓存 + 随机过期时间】 ====================
        // 情况一：数据库里查不到数据（比如有人拿一个不存在的分类id反复请求）
        //        也照样往redis里写一个空集合，并设置较短的过期时间（5分钟）。
        //        这样接下来5分钟内的重复请求都会被redis挡住，不会打到数据库 —— 这就是空值缓存防穿透。
        //        过期时间必须短，否则后续这个分类真的上架了菜品，会长时间读不到数据。
        if (list == null || list.isEmpty()) {
            List<DishVO> emptyList = list == null ? new ArrayList<DishVO>() : list;
            log.info("数据库中没有该分类下的起售菜品，写入空值缓存，分类id：{}，过期时间：{}秒",
                    categoryId, RedisConstants.DISH_CACHE_NULL_TTL);
            redisTemplate.opsForValue().set(key, emptyList, RedisConstants.DISH_CACHE_NULL_TTL, TimeUnit.SECONDS);
            return Result.success(emptyList);
        }

        // 情况二：数据库里查到了数据
        //        过期时间 = 基础时间(1800秒) + 随机抖动(0~300秒)，
        //        这样每一份缓存的失效时间都不一样，不会出现"一大批缓存同时失效、
        //        请求瞬间全部涌向数据库"的情况 —— 这就是随机过期时间防雪崩。
        long ttl = RedisConstants.DISH_CACHE_BASE_TTL
                + ThreadLocalRandom.current().nextLong(RedisConstants.DISH_CACHE_RANDOM_TTL + 1);
        log.info("菜品缓存未命中，回源数据库并回填缓存，分类id：{}，过期时间：{}秒", categoryId, ttl);
        redisTemplate.opsForValue().set(key, list, ttl, TimeUnit.SECONDS);

        //原来没有设置过期时间、也没有做随机抖动的写法，保留作对比：
//        redisTemplate.opsForValue().set(key,list);

        return Result.success(list);
    }

    /**
     * 根据菜品id查询菜品详情
     * <p>
     * 【补充说明】这个接口是本次补全新增的。
     * 它和上面的 /list 是两条不同的查询链路，防穿透手段也各挡一层：
     * - /list 按「分类id」查，用【空值缓存】防穿透；
     * - /{id} 按「菜品id」查，用【布隆过滤器】兜底防穿透。
     *
     * @param id 菜品id
     * @return 菜品详情（含口味）
     */
    @GetMapping("/{id}")
    @ApiOperation("根据菜品id查询菜品详情")
    @RateLimit(window = 60, maxCount = 100)
    public Result<DishVO> getById(@PathVariable Long id) {

        // ==================== 【补全3：布隆过滤器兜底，防止缓存穿透】 ====================
        // 布隆过滤器里存的是项目启动时从数据库加载的全部菜品id。
        // mightContain 返回 false 表示这个id「一定不存在」，
        // 这时候连缓存和数据库都不用查，直接拒绝，恶意请求就被挡在最前面了。
        if (!dishBloomFilter.mightContain(id)) {
            log.warn("布隆过滤器拦截了不存在的菜品id：{}", id);
            throw new BaseException(MessageConstant.DISH_NOT_FOUND);
        }

        //构造redis中的key 规则：dish_detail_菜品id
        String key = RedisConstants.DISH_DETAIL_CACHE_KEY_PREFIX + id;

        //先查缓存
        DishVO dishVO = (DishVO) redisTemplate.opsForValue().get(key);
        if (dishVO != null) {
            log.info("菜品详情缓存命中，菜品id：{}", id);
            return Result.success(dishVO);
        }

        //缓存未命中，回源数据库
        dishVO = dishService.getByIdWithFlavor(id);
        if (dishVO == null) {
            //数据库里也没有（说明是布隆过滤器的1%误判），直接返回不存在。
            //这里不需要再写空值缓存：绝大多数不存在的id已经被布隆过滤器挡掉了，
            //剩下这极小部分误判请求打到数据库，压力可以忽略。
            throw new BaseException(MessageConstant.DISH_NOT_FOUND);
        }

        //回填缓存，同样使用「基础时间 + 随机抖动」的过期时间防止雪崩
        long ttl = RedisConstants.DISH_CACHE_BASE_TTL
                + ThreadLocalRandom.current().nextLong(RedisConstants.DISH_CACHE_RANDOM_TTL + 1);
        log.info("菜品详情缓存未命中，回源数据库并回填缓存，菜品id：{}，过期时间：{}秒", id, ttl);
        redisTemplate.opsForValue().set(key, dishVO, ttl, TimeUnit.SECONDS);

        return Result.success(dishVO);
    }

}
