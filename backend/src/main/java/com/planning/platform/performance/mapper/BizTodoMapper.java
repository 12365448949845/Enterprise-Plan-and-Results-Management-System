package com.planning.platform.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.performance.domain.BizTodo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizTodoMapper extends BaseMapper<BizTodo> {

    @Select("SELECT * FROM biz_todo WHERE id = #{id} FOR UPDATE")
    BizTodo selectForUpdateById(@Param("id") Long id);
}
