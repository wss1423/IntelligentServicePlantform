package com.sky.controller.admin;


import com.github.pagehelper.Page;
import com.sky.component.DishBloomFilter;
import com.sky.constant.RedisConstants;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 菜品管理
 */
@RestController
@Slf4j
@Api("菜品相关接口")
@RequestMapping("/admin/dish")
public class DishController {

    @Autowired
    private DishService dishService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 菜品布隆过滤器：新增菜品时要把新菜品的id同步写进去
     */
    @Autowired
    private DishBloomFilter dishBloomFilter;

    /**
     * 新增菜品
     * @param dishDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增菜品")
    public Result save(@RequestBody DishDTO dishDTO){
        log.info("新增菜品：{}",dishDTO);
        //说明：saveWithFlavor 的返回值由 void 改成 Long，这里接收新菜品的id，
        //用于把新菜品同步写入布隆过滤器（原写法 dishService.saveWithFlavor(dishDTO); 也能编译，只是拿不到id）
        Long dishId = dishService.saveWithFlavor(dishDTO);

        //同步写入布隆过滤器，否则新菜品会被布隆过滤器误判为"不存在"而被拦掉
        dishBloomFilter.add(dishId);

        //清理缓存数据
        String key = RedisConstants.DISH_CACHE_KEY_PREFIX + dishDTO.getCategoryId();
        redisTemplate.delete(key);
        return Result.success();
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO){
        log.info("菜品分页查询：{}",dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 删除菜品的批量删除
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("删除菜品")
    public Result delete(@RequestParam List<Long> ids){
        log.info("菜品批量删除：{}",ids);
        dishService.deleteBatch(ids);

        //删除redis缓存数据 所有以dish_开头的key
        // ==================== 【修复】通配符漏写 ====================
        // 原来的写法是 keys("dish_")，少了通配符 *，而redis里真实的key是 dish_1、dish_2 这种，
        // 所以这行删除实际上一个key都没删到，缓存清不掉。
        // 下面这几行是原来的写法，保留作对比：
//        Set keys = redisTemplate.keys("dish_");
//        redisTemplate.delete(keys);
        Set keys = redisTemplate.keys(RedisConstants.DISH_CACHE_KEY_PREFIX + "*");
        redisTemplate.delete(keys);


        return Result.success();
    }

    /**
     * 根据id查询菜品数据
     */
    @GetMapping("/{id}")
    @ApiOperation("查询菜品数据根据id")
    public Result<DishVO> getById(@PathVariable Long id){
        log.info("根据id查询菜品数据：{}",id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }

    @PutMapping
    @ApiOperation("修改菜品")
    public Result update(@RequestBody DishDTO dishDTO){
        log.info("修改菜品：{}",dishDTO);
        dishService.updateWithFlavor(dishDTO);

        //删除redis缓存
        // ==================== 【修复】通配符漏写，同上 ====================
        // 原来的写法，保留作对比：
//        Set keys = redisTemplate.keys("dish_");
//        redisTemplate.delete(keys);
        Set keys = redisTemplate.keys(RedisConstants.DISH_CACHE_KEY_PREFIX + "*");
        redisTemplate.delete(keys);

        return Result.success();
    }

    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<Dish>> list(Long categoryId){
        List<Dish> list = dishService.list(categoryId);
        return Result.success(list);
    }

    /**
     * 菜品的起售或停售
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("菜品起售或停售")
    public Result<String> startOrStop(@PathVariable Integer status,Long id){
        dishService.startOrStop(status,id);

        //删除redis缓存
        // ==================== 【修复】通配符漏写，同上 ====================
        // 原来的写法，保留作对比：
//        Set keys = redisTemplate.keys("dish_");
//        redisTemplate.delete(keys);
        Set keys = redisTemplate.keys(RedisConstants.DISH_CACHE_KEY_PREFIX + "*");
        redisTemplate.delete(keys);

        return Result.success();
    }
}
