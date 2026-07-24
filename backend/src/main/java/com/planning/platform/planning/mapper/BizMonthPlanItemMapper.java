package com.planning.platform.planning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizMonthPlanItemMapper extends BaseMapper<BizMonthPlanItem> {

    @Select("SELECT * FROM biz_month_plan_item WHERE id = #{id} FOR UPDATE")
    BizMonthPlanItem selectForUpdateById(@Param("id") Long id);
}
