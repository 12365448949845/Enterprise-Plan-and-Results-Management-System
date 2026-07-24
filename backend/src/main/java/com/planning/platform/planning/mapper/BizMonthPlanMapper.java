package com.planning.platform.planning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.planning.domain.BizMonthPlan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizMonthPlanMapper extends BaseMapper<BizMonthPlan> {

    @Select("SELECT * FROM biz_month_plan WHERE id = #{id} FOR UPDATE")
    BizMonthPlan selectForUpdateById(@Param("id") Long id);
}
