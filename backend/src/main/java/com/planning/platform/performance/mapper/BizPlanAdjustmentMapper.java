package com.planning.platform.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.performance.domain.BizPlanAdjustment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizPlanAdjustmentMapper extends BaseMapper<BizPlanAdjustment> {

    @Select("SELECT * FROM biz_plan_adjustment WHERE id = #{id} FOR UPDATE")
    BizPlanAdjustment selectForUpdateById(@Param("id") Long id);
}
