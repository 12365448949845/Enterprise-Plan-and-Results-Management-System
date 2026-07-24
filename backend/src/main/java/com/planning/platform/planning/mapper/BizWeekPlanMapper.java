package com.planning.platform.planning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.planning.domain.BizWeekPlan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizWeekPlanMapper extends BaseMapper<BizWeekPlan> {

    @Select("SELECT * FROM biz_week_plan WHERE id = #{id} FOR UPDATE")
    BizWeekPlan selectForUpdateById(@Param("id") Long id);
}
