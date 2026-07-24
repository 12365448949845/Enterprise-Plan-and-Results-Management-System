package com.planning.platform.planning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.planning.domain.BizDayPlan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizDayPlanMapper extends BaseMapper<BizDayPlan> {

    @Select("SELECT * FROM biz_day_plan WHERE id = #{id} FOR UPDATE")
    BizDayPlan selectForUpdateById(@Param("id") Long id);
}
