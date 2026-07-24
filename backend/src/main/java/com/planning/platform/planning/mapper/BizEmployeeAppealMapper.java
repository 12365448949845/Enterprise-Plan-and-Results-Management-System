package com.planning.platform.planning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.planning.domain.BizEmployeeAppeal;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizEmployeeAppealMapper extends BaseMapper<BizEmployeeAppeal> {

    @Select("SELECT * FROM biz_employee_appeal WHERE id = #{id} FOR UPDATE")
    BizEmployeeAppeal selectForUpdateById(@Param("id") Long id);
}
