package com.sky.mapper;

import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishFlavorMapper {


    /**
     * 批量插入口味数据
     * @param flavors
     */
    void insertBatch(List<DishFlavor> flavors);

    /**
     * 删除口味
     * @param DishId
     */
    @Delete("delete from dish_flavor where dish_id = #{DishId}")
    void deleteByDishId(Long DishId);

    /**
     * 根据集合批量删除
     * @param ids
     */
    void deleteByDishIds(List<Long> ids);

    /**
     * 根据菜品id来查询对应的口味
     * @param dishId
     * @return
     */
    @Select("select * from dish_flavor where dish_id = #{dishId}")
    List<DishFlavor> getByDishId(Long dishId);
}
