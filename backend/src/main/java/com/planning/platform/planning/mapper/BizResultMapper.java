package com.planning.platform.planning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.planning.domain.BizResult;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizResultMapper extends BaseMapper<BizResult> {

    @Select("SELECT * FROM biz_result WHERE id = #{id} FOR UPDATE")
    BizResult selectForUpdateById(@Param("id") Long id);
}
