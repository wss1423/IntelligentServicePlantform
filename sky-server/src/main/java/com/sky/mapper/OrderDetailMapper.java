package com.sky.mapper;

import com.sky.entity.OrderDetail;
import com.sky.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderDetailMapper {

    /**
     * 插入多条订单明细数据
     * @param orderDetailList
     */
    void insertBatch(List<OrderDetail> orderDetailList);

    /**
     * 查询订单详情表的
     * @param id
     * @return
     */
    @Select("select * from order_detail where id = #{id}")
    List<OrderDetail> getByOrderId(Long id);


}
