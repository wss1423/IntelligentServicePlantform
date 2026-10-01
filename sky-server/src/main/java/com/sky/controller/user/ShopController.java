package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "店铺相关接口")
@Slf4j
public class ShopController {

    public static final String KEY = "SHOP_STATUS";


    @Autowired
    private RedisTemplate redisTemplate;


    @GetMapping("/status")
    @ApiOperation("获取店铺营业状态")
    public Result<Integer> getStatus(){
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY);
        //【修复】redis里还没有这个key时（比如刚启动、刚换过redis的database），status 是 null，
        //原来的 status == 1 会触发 Integer 自动拆箱，直接抛 NullPointerException，
        //导致用户端一进首页调这个接口就500。这里补一个兜底值。
        //原来的写法保留作对比：
//        log.info("获取店铺的营业状态为：{}",status == 1 ? "营业中" : "打烊中");
        if (status == null) {
            log.info("店铺营业状态在redis中不存在（key：{}），按打烊中处理", KEY);
            status = StatusConstant.DISABLE;
        }
        log.info("获取店铺的营业状态为：{}", status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }



}
