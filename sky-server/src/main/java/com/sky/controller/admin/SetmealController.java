package com.sky.controller.admin;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
//说明：save() 上的 @Cacheable 已经去掉（见方法上的注释），这里保留 import 方便你对比、恢复
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/setmeal")
@Api("套餐相关接口")
@Slf4j
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    @Autowired
    private DishService dishService;


    /**
     * 新增套餐
     */
    @PostMapping
    // ==================== 【修复】写接口上误用了 @Cacheable ====================
    // @Cacheable 的语义是"先查缓存，查到了就直接返回、不再执行方法体"。
    // 挂在"新增套餐"这种写接口上有两个严重问题：
    // 1、第一次新增后会把返回值缓存起来，之后用同一个 categoryId 再新增套餐时，
    //    方法体根本不会执行（套餐压根没入库），但接口却返回成功；
    // 2、它和 user/SetmealController.list() 共用同一个 cacheNames="setmealName"、同一个 key（分类id），
    //    管理端新增时缓存进去的是 Result 对象，用户端查套餐时就会把这个 Result 当成套餐列表返回。
    // 所以这里把它去掉，只保留下面几个 @CacheEvict（数据变更时清理缓存）。
    // 原来的写法，保留作对比：
//    @Cacheable(cacheNames = "setmealName",key = "#setmealDTO.categoryId")
    public Result save(@RequestBody SetmealDTO setmealDTO){
        log.info("开始新增套餐：{}",setmealDTO);
        setmealService.save(setmealDTO);
        return Result.success();
    }

    /**
     * 分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("分页查询")
    public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 删除套餐
     */
    @DeleteMapping
    @CacheEvict(cacheNames = "setmealName",allEntries = true)
    public Result delete(@RequestParam List<Long> ids){
        log.info("开始删除菜品：{}",ids);
        setmealService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 根据id查询套餐，用于修改页面回显数据
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询套餐")
    public Result<SetmealVO> getById(@PathVariable Long id) {
        SetmealVO setmealVO = setmealService.getByIdWithDish(id);
        return Result.success(setmealVO);
    }

    /**
     * 修改套餐
     *
     * @param setmealDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改套餐")
    @CacheEvict(cacheNames = "setmealName",allEntries = true)
    public Result update(@RequestBody SetmealDTO setmealDTO) {
        setmealService.update(setmealDTO);
        return Result.success();
    }

    /**
     * 套餐起售停售
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("套餐起售停售")
    @CacheEvict(cacheNames = "setmealName",allEntries = true)
    public Result startOrStop(@PathVariable Integer status, Long id) {
        setmealService.startOrStop(status, id);
        return Result.success();
    }
}
