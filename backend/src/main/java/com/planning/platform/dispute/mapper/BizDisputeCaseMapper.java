package com.planning.platform.dispute.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.dispute.domain.BizDisputeCase;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizDisputeCaseMapper extends BaseMapper<BizDisputeCase> {
    @Select("SELECT * FROM biz_dispute_case WHERE id = #{id} FOR UPDATE")
    BizDisputeCase selectForUpdateById(@Param("id") Long id);
}
