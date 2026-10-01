package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;

import java.util.List;

public interface DishService {

    /**
     * 新增菜品以及对应的口味
     * 说明：返回值由 void 改成 Long，把新菜品的id返回出去，
     * 方便调用方把新菜品id同步写入布隆过滤器；
     * 原来那种"忽略返回值直接调用"的写法依然可以正常编译
     * @return 新增菜品的id
     */
    public Long saveWithFlavor(DishDTO dishDTO);


    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 菜品的批量删除
     * @param ids
     */
    void deleteBatch(List<Long> ids);

    /**
     * 根据id查询菜品数据
     * @param id
     * @return
     */
    DishVO getByIdWithFlavor(Long id);

    /**
     * 修改菜品信息及口味
     * @param dishDTO
     */
    void updateWithFlavor(DishDTO dishDTO);


    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    List<Dish> list(Long categoryId);

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    List<DishVO> listWithFlavor(Dish dish);

    /**
     * 菜品的起售或停售
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);
}
