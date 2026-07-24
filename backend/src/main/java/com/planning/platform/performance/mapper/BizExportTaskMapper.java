package com.planning.platform.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.performance.domain.BizExportTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizExportTaskMapper extends BaseMapper<BizExportTask> {

    @Select("SELECT * FROM biz_export_task WHERE id = #{id} FOR UPDATE")
    BizExportTask selectForUpdateById(@Param("id") String id);
}
